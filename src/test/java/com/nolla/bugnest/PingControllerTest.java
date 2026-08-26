package com.nolla.bugnest;

import com.nolla.bugnest.controller.PingController;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PingControllerTest {
    @Test
    void addTwoNumbers(){
        PingController controller = new PingController();

        int actual = controller.add(2,3);
        assertEquals(5,actual);
    }

    @Test
    void pingReturnExpectedData(){
        PingController controller = new PingController();

        Map<String, String> actual = controller.ping();
        assertEquals("pong", actual.get("data"));
        assertEquals("ok", actual.get("message"));
    }

}
