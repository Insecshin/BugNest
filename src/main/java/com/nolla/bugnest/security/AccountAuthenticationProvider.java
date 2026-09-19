package com.nolla.bugnest.security;

import com.nolla.bugnest.model.Account;
import com.nolla.bugnest.repository.AccountRepository;
import com.nolla.bugnest.service.PasswordPolicy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 这个类负责“核对登录凭据”：用户名/邮箱是否对应账户，以及密码是否正确。
 *
 * <p>它是 Spring Security 的一个认证提供者（Provider）。登录过滤器把请求中的
 * identifier 和 password 包装成 {@link UsernamePasswordAuthenticationToken}，交给
 * {@code AuthenticationManager}；Manager 再找到能处理这种 Token 的 Provider，调用本类的
 * {@link #authenticate(Authentication)} 方法。</p>
 *
 * <p>注意边界：本类只判断“是谁”并返回认证结果，不创建 Session，也不手动保存
 * {@code SecurityContext}。认证过滤器会负责这些后续工作。</p>
 */
@Component // 告诉 Spring 自动发现并创建这个类的 Bean，其他 Bean 才能注入它。
public class AccountAuthenticationProvider implements AuthenticationProvider {
    /**
     * 合法用户名格式：小写英文字母 a-z、数字 0-9、下划线，长度 1 到 30。
     * Pattern 是编译后的正则对象；放在 static final 中，避免每次登录都重新编译。
     */
    private static final Pattern USER_NAME_PATTERN = Pattern.compile("[a-z0-9_]{1,30}");

    // 以下是本类工作时要用的协作者。它们由 Spring 在创建本类时传进来（构造器注入）。
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginRateLimiter rateLimiter;

    /**
     * 一个预先生成好的 Argon2id 密码哈希，专门用于“账户不存在”的情况。
     * 这样未知账户也会执行一次密码哈希校验，减少攻击者通过响应时间猜账户是否存在的机会。
     */
    private final String dummyPasswordHash;

    /**
     * 构造器：Spring 根据参数类型找到对应 Bean，并把它们传进来。
     * 参数不用手工 new；例如 accounts 是 JDBC Repository，passwordEncoder 是项目配置的密码编码器。
     */
    public AccountAuthenticationProvider(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            LoginRateLimiter rateLimiter,
            String dummyPasswordHash
    ) {
        // 把传进来的对象保存到成员变量里，供下面的 authenticate/findAccount 使用。
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.rateLimiter = rateLimiter;
        this.dummyPasswordHash = dummyPasswordHash;
    }

    /**
     * 真正执行一次认证。成功时返回“已认证”的 Authentication；失败时抛出认证异常。
     *
     * @param authentication 上游登录过滤器提供的凭据；此处 principal 是登录标识，credentials 是密码
     * @return 认证成功后的 Authentication，里面放账户身份，而不是明文密码
     * @throws AuthenticationException 密码错误、账户冷却或认证相关基础设施不可用时由 Spring Security 处理
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // getPrincipal() 的返回类型是 Object，所以这里要转成 String。
        // strip() 去掉登录标识两侧空白；toLowerCase(Locale.ROOT) 用固定语言规则转小写，
        // 避免服务器系统语言不同导致用户名/邮箱规范化结果不同。
        String identifier = ((String) authentication.getPrincipal()).strip().toLowerCase(Locale.ROOT);

        // getCredentials() 同样是 Object，这里取出原始密码并做与注册时一致的 NFC 规范化。
        // 不调用 strip()：密码里的空格是密码本身的一部分，不能擅自删除。
        String password = passwordPolicy.normalizeForAuthentication((String) authentication.getCredentials());

        // 按标识查账户。Optional<Account> 表示“可能找到 Account，也可能什么都没找到”，
        // 不用 null 表达“没找到”。
        Optional<Account> account = findAccount(identifier);

        // 选出这个标识对应的限流键：
        // 1. 已知账户用数据库账户 ID，所以邮箱和用户名会落到同一个键；
        // 2. 未知账户用 HMAC 计算的键，不能把原始邮箱/用户名直接写入 Redis。
        // map(...) 是“有账户时转换成账户键”；orElseGet(...) 是“没有账户时才计算未知标识键”。
        String rateLimitKey = account.map(value -> rateLimiter.accountKey(value.getId()))
                .orElseGet(() -> rateLimiter.unknownIdentifierKey(identifier));

        // 先检查账户是否正在冷却中。若在冷却期，直接拒绝，避免继续做耗时的 Argon2 校验。
        rateLimiter.checkAccount(rateLimitKey);

        // 真实账户使用数据库中的密码哈希；账户不存在则使用 dummy 哈希。
        // map(Account::getPasswordHash) 是方法引用，等价于 map(value -> value.getPasswordHash())。
        // orElse(...) 在 Optional 为空时提供备用值。
        String passwordHash = account.map(Account::getPasswordHash).orElse(dummyPasswordHash);

        // matches(用户输入的密码, 已保存的哈希) 会按哈希自身携带的参数重新计算并比较。
        // 不能把密码和哈希直接做字符串相等比较，因为 Argon2 哈希包含随机盐。
        // 这一行无论账户是否存在都会执行；未知账户比较的是 dummyPasswordHash。
        boolean matches = passwordEncoder.matches(password, passwordHash);

        // || 是“或”：账户不存在，或者密码不匹配，都进入同一个失败分支。
        // 对外统一抛 BadCredentialsException，避免告诉调用者到底是账户不存在还是密码错误。
        if (account.isEmpty() || !matches) {
            // 记录一次失败；真实账户按账户 ID 计数，未知标识按 HMAC 键计数。
            rateLimiter.recordFailure(rateLimitKey);
            // Spring Security 会把该异常交给登录失败处理器，最终返回统一的 401 响应。
            throw new BadCredentialsException("Invalid username or password");
        }

        // 到这里表示账户存在且密码正确。成功登录清除此前连续失败/冷却状态。
        rateLimiter.recordSuccess(rateLimitKey);

        // 因为上面的失败分支已经处理了 Optional 为空的情况，此处一定能取到账户。
        // orElseThrow() 在“本应有值但实际没有”时抛异常，避免静默产生错误认证结果。
        Account value = account.orElseThrow();

        // 创建 Spring Security 认可的“已认证” Token：
        // - principal：当前用户身份，只放 ID、用户名、昵称，不放邮箱或密码哈希；
        // - credentials：传 null，认证成功后不再保留密码；
        // - authorities：当前项目没有角色/权限体系，因此是空集合。
        // authenticated(...) 创建的 Token 已标记为认证成功，供过滤器保存进 SecurityContext。
        return UsernamePasswordAuthenticationToken.authenticated(
                new AccountPrincipal(value.getId(), value.getUserName(), value.getNickname()),
                null,
                AuthorityUtils.NO_AUTHORITIES
        );
    }

    /**
     * Spring Security 会用这个方法询问：“本 Provider 能不能处理这种认证对象？”
     * 只接受 UsernamePasswordAuthenticationToken 本身或它的子类。
     */
    @Override
    public boolean supports(Class<?> authentication) {
        // Class<?> 表示“某个类型的 Class 对象”，问的是传入的认证类型是否兼容。
        // isAssignableFrom 用于判断类型兼容关系；不是比较两个 Class 对象是否完全相同。
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * 按输入格式决定查询邮箱还是用户名。
     * 返回 Optional：格式不合法或数据库无记录时返回 Optional.empty()，不会返回 null。
     */
    private Optional<Account> findAccount(String identifier) {
        // 当前约定：包含 @ 的标识按邮箱处理。
        if (identifier.contains("@")) {
            // 超过邮箱最大长度就当作“账户不存在”，不向数据库发起查询。
            // 三元表达式 condition ? A : B 的意思是：条件成立取 A，否则取 B。
            return identifier.length() <= 254 ? accounts.findByEmail(identifier) : Optional.empty();
        }

        // 不包含 @ 的标识按用户名处理。进入本方法前 identifier 已转成小写，
        // 因此用户输入大写用户名会按小写查找；matches() 要求规范化后的整个字符串
        // 都符合正则，所以空格、横线和其他不允许的字符会使它按“未知账户”处理。
        return USER_NAME_PATTERN.matcher(identifier).matches()
                ? accounts.findByUserName(identifier)
                : Optional.empty();
    }
}
