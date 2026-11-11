package com.example.arrivalalarm;

import org.junit.Test;

public final class GradleCoreLogicTest {
    @Test
    public void existingCoreSuitePasses() {
        CoreLogicTest.main(new String[0]);
        LocationRecoveryPolicyTest.main(new String[0]);
    }
}
