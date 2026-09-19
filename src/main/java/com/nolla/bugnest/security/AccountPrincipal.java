package com.nolla.bugnest.security;

import java.io.Serializable;

public record AccountPrincipal(Long id, String userName, String nickname) implements Serializable {
}
