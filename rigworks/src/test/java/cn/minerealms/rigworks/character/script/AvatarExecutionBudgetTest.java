package cn.minerealms.rigworks.character.script;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AvatarExecutionBudgetTest {
    @Test void schedulingAndGcPauseDoNotConsumeMeasuredCpuAllowance() {
        var wall=new AtomicLong(100);var cpu=new AtomicLong(100);var allocated=new AtomicLong(100);
        var budget=new AvatarExecutionBudget(wall::get,cpu::get,allocated::get);budget.arm(100_000,10_000_000);
        wall.addAndGet(1_000_000_000);cpu.addAndGet(1_000_000);
        assertDoesNotThrow(()->budget.observe(100));
        cpu.addAndGet(10_000_000);
        var error=assertThrows(AvatarExecutionBudget.Exceeded.class,budget::check);assertTrue(error.getMessage().contains("CPU"));
    }
    @Test void instructionLimitAndWallWatchdogStillStopRunawayExecution() {
        var wall=new AtomicLong();var cpu=new AtomicLong();
        var budget=new AvatarExecutionBudget(wall::get,cpu::get,()->-1);budget.arm(100_000,10_000_000);
        assertThrows(AvatarExecutionBudget.Exceeded.class,()->budget.observe(100_001));
        budget.arm(100_000,10_000_000);wall.addAndGet(5_000_000_001L);
        var error=assertThrows(AvatarExecutionBudget.Exceeded.class,budget::check);assertTrue(error.getMessage().contains("watchdog"));
    }
    @Test void unavailableCpuCounterUsesConservativeBoundedWallFallback() {
        var wall=new AtomicLong();var budget=new AvatarExecutionBudget(wall::get,()->-1,()->-1);budget.arm(100_000,10_000_000);
        wall.addAndGet(100_000_000);assertDoesNotThrow(budget::check);
        wall.addAndGet(200_000_000);assertThrows(AvatarExecutionBudget.Exceeded.class,budget::check);
    }
    @Test void allocationLimitUsesOwnerThreadCounterAndResetsBetweenExecutions() {
        var allocated=new AtomicLong(1024);var budget=new AvatarExecutionBudget(()->0,()->0,allocated::get);budget.arm(100_000,10_000_000);
        allocated.addAndGet(32L*1024*1024+1);assertThrows(AvatarExecutionBudget.Exceeded.class,budget::check);
        budget.arm(100_000,10_000_000);assertDoesNotThrow(budget::check);
    }
}
