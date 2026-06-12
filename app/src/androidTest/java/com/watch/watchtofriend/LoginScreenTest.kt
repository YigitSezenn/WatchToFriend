package com.watch.watchtofriend

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.watch.watchtofriend.ui.auth.LoginScreen
import org.junit.Rule
import org.junit.Test

/**
 * Firebase ağı gerektirmeyen, offline çalışan basit Compose UI testleri.
 * Sadece arayüz öğelerinin görünürlüğünü ve mod geçişini doğrular;
 * gerçek login/register ağ çağrısı tetiklenmez.
 */
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setLoginContent() {
        composeRule.setContent {
            LoginScreen(onLoginSuccess = {})
        }
    }

    @Test
    fun loginScreen_showsGoogleAndEmailLoginOptions() {
        setLoginContent()

        // Google ile giriş butonu görünür
        composeRule.onNodeWithText("Google ile Giriş Yap").assertIsDisplayed()

        // E-posta ve Şifre alanları görünür
        composeRule.onNodeWithText("E-posta").assertIsDisplayed()
        composeRule.onNodeWithText("Şifre").assertIsDisplayed()

        // "Giriş Yap" hem başlıkta hem butonda geçer -> en az bir tane görünür olmalı
        composeRule.onAllNodesWithText("Giriş Yap")[0].assertIsDisplayed()

        // Kayıt moduna geçiş linki görünür
        composeRule.onNodeWithText("Hesap oluştur").assertIsDisplayed()
    }

    @Test
    fun loginScreen_togglesToRegisterMode_showsUsernameField() {
        setLoginContent()

        // Başlangıçta Kullanıcı Adı alanı YOK (login modu)
        composeRule.onAllNodesWithText("Kullanıcı Adı").assertCountEquals(0)

        // Kayıt moduna geç
        composeRule.onNodeWithText("Hesap oluştur").performClick()

        // Kullanıcı Adı alanı ve Kayıt Ol butonu artık görünür
        composeRule.onNodeWithText("Kullanıcı Adı").assertIsDisplayed()
        composeRule.onNodeWithText("Kayıt Ol").assertIsDisplayed()

        // Geri dönüş linki güncellenmiş
        composeRule.onNodeWithText("Zaten hesabım var, giriş yap").assertIsDisplayed()
    }

    @Test
    fun loginScreen_togglesBackToLoginMode() {
        setLoginContent()

        composeRule.onNodeWithText("Hesap oluştur").performClick()
        composeRule.onNodeWithText("Kayıt Ol").assertIsDisplayed()

        // Tekrar login moduna dön
        composeRule.onNodeWithText("Zaten hesabım var, giriş yap").performClick()

        // Kullanıcı Adı alanı tekrar gizlenir
        composeRule.onAllNodesWithText("Kullanıcı Adı").assertCountEquals(0)
        composeRule.onNodeWithText("Hesap oluştur").assertIsDisplayed()
    }
}
