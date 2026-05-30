package com.auction.common.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test toàn diện cho {@link Wallet}.
 *
 * <p>Kiểm tra các trường hợp biên (edge cases): số âm, zero, vượt số dư,
 * chuỗi thao tác phức tạp, và tính nhất quán của balance + frozenAmount.</p>
 */
@DisplayName("Wallet — Unit Tests")
class WalletTest {

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet();
    }

    // -------------------------------------------------------------------------
    // Trạng thái ban đầu
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Khởi tạo: balance và frozenAmount đều bằng 0")
    void initialState_shouldBeZero() {
        assertEquals(0.0, wallet.getBalance(),      "Balance ban đầu = 0");
        assertEquals(0.0, wallet.getFrozenAmount(), "FrozenAmount ban đầu = 0");
    }

    // -------------------------------------------------------------------------
    // Deposit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deposit số dương: tăng balance")
    void deposit_positiveAmount_shouldIncreaseBalance() {
        wallet.deposit(1_000_000.0);
        assertEquals(1_000_000.0, wallet.getBalance());
    }

    @Test
    @DisplayName("Deposit số âm: không thay đổi balance")
    void deposit_negativeAmount_shouldBeIgnored() {
        wallet.deposit(-500.0);
        assertEquals(0.0, wallet.getBalance(), "Deposit âm bị bỏ qua");
    }

    @Test
    @DisplayName("Deposit zero: không thay đổi balance")
    void deposit_zero_shouldBeIgnored() {
        wallet.deposit(0.0);
        assertEquals(0.0, wallet.getBalance());
    }

    @Test
    @DisplayName("Nhiều lần deposit: cộng dồn đúng")
    void deposit_multipleTimes_shouldAccumulate() {
        wallet.deposit(500_000.0);
        wallet.deposit(300_000.0);
        wallet.deposit(200_000.0);
        assertEquals(1_000_000.0, wallet.getBalance());
    }

    // -------------------------------------------------------------------------
    // Withdraw
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Withdraw hợp lệ: trừ balance, trả true")
    void withdraw_validAmount_shouldDecreaseBalanceAndReturnTrue() {
        wallet.deposit(1_000_000.0);
        assertTrue(wallet.withdraw(400_000.0));
        assertEquals(600_000.0, wallet.getBalance());
    }

    @Test
    @DisplayName("Withdraw vượt số dư: bị từ chối, balance không đổi")
    void withdraw_exceedingBalance_shouldReturnFalseAndLeaveBalanceUnchanged() {
        wallet.deposit(500_000.0);
        assertFalse(wallet.withdraw(700_000.0));
        assertEquals(500_000.0, wallet.getBalance());
    }

    @Test
    @DisplayName("Withdraw số âm: bị từ chối")
    void withdraw_negativeAmount_shouldReturnFalse() {
        wallet.deposit(1_000_000.0);
        assertFalse(wallet.withdraw(-100.0));
        assertEquals(1_000_000.0, wallet.getBalance());
    }

    @Test
    @DisplayName("Withdraw zero: bị từ chối")
    void withdraw_zero_shouldReturnFalse() {
        wallet.deposit(500_000.0);
        assertFalse(wallet.withdraw(0.0));
    }

    @Test
    @DisplayName("Withdraw chính xác bằng balance: thành công, balance = 0")
    void withdraw_exactBalance_shouldSucceed() {
        wallet.deposit(500_000.0);
        assertTrue(wallet.withdraw(500_000.0));
        assertEquals(0.0, wallet.getBalance());
    }

    // -------------------------------------------------------------------------
    // Freeze
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Freeze hợp lệ: chuyển tiền từ balance sang frozenAmount")
    void freeze_validAmount_shouldTransferFromBalanceToFrozen() {
        wallet.deposit(1_000_000.0);
        assertTrue(wallet.freeze(300_000.0));
        assertEquals(700_000.0, wallet.getBalance(),      "Balance giảm");
        assertEquals(300_000.0, wallet.getFrozenAmount(), "FrozenAmount tăng");
    }

    @Test
    @DisplayName("Freeze vượt số dư: bị từ chối, không thay đổi state")
    void freeze_exceedingBalance_shouldReturnFalseAndLeaveStateUnchanged() {
        wallet.deposit(200_000.0);
        assertFalse(wallet.freeze(500_000.0));
        assertEquals(200_000.0, wallet.getBalance());
        assertEquals(0.0,       wallet.getFrozenAmount());
    }

    @Test
    @DisplayName("Freeze số âm: bị từ chối")
    void freeze_negativeAmount_shouldReturnFalse() {
        wallet.deposit(500_000.0);
        assertFalse(wallet.freeze(-100.0));
        assertEquals(500_000.0, wallet.getBalance());
        assertEquals(0.0,       wallet.getFrozenAmount());
    }

    // -------------------------------------------------------------------------
    // Release
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Release hợp lệ: chuyển tiền từ frozenAmount về balance")
    void release_validAmount_shouldTransferFromFrozenToBalance() {
        wallet.deposit(1_000_000.0);
        wallet.freeze(400_000.0);

        wallet.release(400_000.0);

        assertEquals(1_000_000.0, wallet.getBalance(),      "Balance được phục hồi đầy đủ");
        assertEquals(0.0,         wallet.getFrozenAmount(), "FrozenAmount về 0");
    }

    @Test
    @DisplayName("Release một phần frozenAmount: chỉ hoàn một phần")
    void release_partialAmount_shouldOnlyReleaseSpecifiedAmount() {
        wallet.deposit(1_000_000.0);
        wallet.freeze(600_000.0);

        wallet.release(200_000.0);

        assertEquals(600_000.0, wallet.getBalance(),       "Balance tăng đúng phần được release");
        assertEquals(400_000.0, wallet.getFrozenAmount(),  "FrozenAmount giảm đúng");
    }

    @Test
    @DisplayName("Release vượt frozenAmount: bị bỏ qua")
    void release_exceedingFrozenAmount_shouldBeIgnored() {
        wallet.deposit(500_000.0);
        wallet.freeze(100_000.0);

        wallet.release(999_000.0); // Nhiều hơn frozen

        // State không được thay đổi
        assertEquals(400_000.0, wallet.getBalance());
        assertEquals(100_000.0, wallet.getFrozenAmount());
    }

    // -------------------------------------------------------------------------
    // Kịch bản tổng hợp (phỏng theo luồng đấu giá thực tế)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Kịch bản đấu giá: deposit → freeze → bị vượt mặt → release → bid lại")
    void scenario_bidThenRefundThenBidAgain_shouldMaintainConsistency() {
        wallet.deposit(1_000_000.0);

        // Bid lần 1: freeze 300k
        assertTrue(wallet.freeze(300_000.0));
        assertEquals(700_000.0, wallet.getBalance());
        assertEquals(300_000.0, wallet.getFrozenAmount());

        // Bị vượt mặt: release 300k
        wallet.release(300_000.0);
        assertEquals(1_000_000.0, wallet.getBalance());
        assertEquals(0.0,         wallet.getFrozenAmount());

        // Bid lần 2 với giá cao hơn: freeze 800k
        assertTrue(wallet.freeze(800_000.0));
        assertEquals(200_000.0, wallet.getBalance());
        assertEquals(800_000.0, wallet.getFrozenAmount());
    }

    @Test
    @DisplayName("Chuỗi thao tác phức tạp: tổng balance + frozen phải không đổi")
    void invariant_totalBalancePlusFrozenShouldEqualTotalDeposited() {
        wallet.deposit(1_000_000.0);
        wallet.freeze(300_000.0);
        wallet.withdraw(100_000.0);   // Rút từ balance còn lại
        wallet.release(100_000.0);    // Hoàn một phần frozen
        wallet.deposit(500_000.0);    // Nạp thêm

        double total = wallet.getBalance() + wallet.getFrozenAmount();
        // Deposit tổng: 1_500_000, Withdraw: 100_000 → còn: 1_400_000
        assertEquals(1_400_000.0, total, 0.001,
                "Tổng balance + frozen phải bằng tổng đã nạp - đã rút");
    }
}
