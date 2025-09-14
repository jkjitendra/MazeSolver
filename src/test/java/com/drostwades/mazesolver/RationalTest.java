package com.drostwades.mazesolver;

import static org.junit.jupiter.api.Assertions.assertEquals;import org.junit.jupiter.api.Test;

public class RationalTest {
    @Test
    void parse_and_ops() {
        var a = MazeSolverUI.Rational.parse("1.25");
        var b = MazeSolverUI.Rational.ofInt(2);
        assertEquals("5/4", a.toString());
        assertEquals("5/2", b.mul(a).toString());
    }
}