package com.smartapply.ida.security.mask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataMaskerTest {

    @Test
    void maskPhoneAndRestore() {
        String text = "联系电话13800138000，备用13912345678";
        SensitiveDataMasker.MaskSession session = SensitiveDataMasker.mask(text);

        assertFalse(session.maskedText().contains("13800138000"));
        assertFalse(session.maskedText().contains("13912345678"));
        assertTrue(session.maskedText().contains("[PHONE_1]"));
        assertTrue(session.maskedText().contains("[PHONE_2]"));

        String restored = SensitiveDataMasker.restore(session.maskedText(), session);
        assertEquals(text, restored);
    }

    @Test
    void sameValueGetsSamePlaceholder() {
        String text = "手机13800138000，再次确认13800138000";
        SensitiveDataMasker.MaskSession session = SensitiveDataMasker.mask(text);

        assertEquals("手机[PHONE_1]，再次确认[PHONE_1]", session.maskedText());
        assertEquals(1, session.mapping().size());
    }

    @Test
    void idCardMaskedBeforePhone() {
        // 18 位身份证内含 11 位数字子串，必须先按身份证匹配
        String idCard = "11010119900307778X";
        SensitiveDataMasker.MaskSession session = SensitiveDataMasker.mask("证件号:" + idCard);

        assertEquals("证件号:[IDCARD_1]", session.maskedText());
        assertEquals(idCard, session.mapping().get("[IDCARD_1]"));
        assertEquals("证件号:" + idCard, SensitiveDataMasker.restore(session.maskedText(), session));
    }

    @Test
    void emailMaskedAndRestored() {
        String text = "邮箱 zhang.san+work@example-company.com 备用";
        SensitiveDataMasker.MaskSession session = SensitiveDataMasker.mask(text);

        assertFalse(session.maskedText().contains("zhang.san"));
        assertTrue(session.maskedText().contains("[EMAIL_1]"));
        assertEquals(text, SensitiveDataMasker.restore(session.maskedText(), session));
    }

    @Test
    void mixedPiiAllMasked() {
        String text = "姓名张三 手机13800138000 身份证11010119900307778X 邮箱a@b.com";
        SensitiveDataMasker.MaskSession session = SensitiveDataMasker.mask(text);

        assertFalse(session.maskedText().matches(".*(?<!\\d)\\d{11}(?!\\d).*"), "不应残留明文手机号");
        assertFalse(session.maskedText().matches(".*(?<!\\d)\\d{17}[\\dXx](?!\\d).*"), "不应残留明文身份证");
        assertFalse(session.maskedText().contains("a@b.com"), "不应残留明文邮箱");
        assertEquals(text, SensitiveDataMasker.restore(session.maskedText(), session));
    }

    @Test
    void nullAndEmptyInput() {
        assertEquals(null, SensitiveDataMasker.mask(null).maskedText());
        assertEquals("", SensitiveDataMasker.mask("").maskedText());
        assertTrue(SensitiveDataMasker.mask(null).mapping().isEmpty());
        // restore 对空会话/空映射原样返回
        assertEquals("abc13800138000", SensitiveDataMasker.restore("abc13800138000", SensitiveDataMasker.mask(null)));
        assertEquals(null, SensitiveDataMasker.restore(null, SensitiveDataMasker.mask("x")));
    }

    @Test
    void restoreWithoutSessionKeepsText() {
        assertEquals("[PHONE_1]", SensitiveDataMasker.restore("[PHONE_1]", null));
    }

    @Test
    void maskMiddleDefaults() {
        assertEquals("138****8000", SensitiveDataMasker.maskMiddle("13800138000"));
        assertEquals("110**********7778", SensitiveDataMasker.maskMiddle("11010119900307778"));
        assertEquals("1234567", SensitiveDataMasker.maskMiddle("1234567")); // 长度不足原样返回
        assertEquals(null, SensitiveDataMasker.maskMiddle(null));
    }

    @Test
    void maskSensitiveIrreversible() {
        String text = "手机13800138000 证件11010119900307778X 邮箱zhang.san@example.com";
        String masked = SensitiveDataMasker.maskSensitive(text);

        assertFalse(masked.contains("13800138000"));
        assertFalse(masked.contains("11010119900307778X"));
        assertFalse(masked.contains("zhang.san@"));
        assertTrue(masked.contains("138****8000"));
        assertTrue(masked.contains("z***@example.com"));
        // 身份证 18 位：前 3 后 4，中间 11 个 *
        assertTrue(masked.contains("110***********778X"));
        assertEquals("", SensitiveDataMasker.maskSensitive(null));
    }
}
