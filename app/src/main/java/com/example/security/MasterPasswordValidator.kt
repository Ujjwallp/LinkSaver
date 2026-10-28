package com.example.security

object MasterPasswordValidator {

    private val COMMON_PASSWORDS = setOf(
        "password1234", "password12345", "123456789012", "1234567890123",
        "qwertyuiop12", "qwertyuiopas", "admin1234567", "admin12345678",
        "linkvault123", "linkvault1234", "masterpassword", "masterpass123",
        "iloveyou1234", "sunshine12345", "princess1234", "dragon123456",
        "welcome12345", "letmein12345", "monkey123456", "football1234"
    )

    enum class PasswordStrength(val label: String, val score: Int) {
        TOO_SHORT("Too Short (< 12 chars)", 0),
        COMMON("Common or Predictable", 1),
        FAIR("Fair", 2),
        GOOD("Good", 3),
        STRONG("Very Strong", 4)
    }

    data class ValidationResult(
        val isValid: Boolean,
        val strength: PasswordStrength,
        val errorMessage: String? = null
    )

    fun validate(password: CharArray): ValidationResult {
        if (password.size < 12) {
            return ValidationResult(
                isValid = false,
                strength = PasswordStrength.TOO_SHORT,
                errorMessage = "Master password must be at least 12 characters long"
            )
        }

        val passString = String(password).lowercase()
        if (COMMON_PASSWORDS.any { passString.contains(it) || it.contains(passString) }) {
            return ValidationResult(
                isValid = false,
                strength = PasswordStrength.COMMON,
                errorMessage = "This password contains common, easily guessed sequences"
            )
        }

        // Evaluate complexity
        var hasUpper = false
        var hasLower = false
        var hasDigit = false
        var hasSymbol = false

        for (c in password) {
            when {
                c.isUpperCase() -> hasUpper = true
                c.isLowerCase() -> hasLower = true
                c.isDigit() -> hasDigit = true
                else -> hasSymbol = true
            }
        }

        var score = 1
        if (password.size >= 14) score++
        if (password.size >= 18) score++
        val varietyCount = listOf(hasUpper, hasLower, hasDigit, hasSymbol).count { it }
        if (varietyCount >= 3) score++

        val strength = when {
            score >= 4 -> PasswordStrength.STRONG
            score == 3 -> PasswordStrength.GOOD
            else -> PasswordStrength.FAIR
        }

        return ValidationResult(
            isValid = true,
            strength = strength,
            errorMessage = null
        )
    }
}
