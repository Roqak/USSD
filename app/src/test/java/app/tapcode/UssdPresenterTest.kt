package app.tapcode

import app.tapcode.engine.UssdPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The presentation layer turns raw USSD text into app-friendly content:
 * headings and amounts for results, cleaned bodies, no codes anywhere.
 */
class UssdPresenterTest {

    @Test
    fun `balance response gets balance heading and naira amount`() {
        val friendly = UssdPresenter.present("Your balance is NGN 1,234.56. Thank you.")
        assertEquals("Your balance", friendly.heading)
        assertEquals("₦1,234.56", friendly.amount)
    }

    @Test
    fun `data bundle response is labelled data balance`() {
        val friendly = UssdPresenter.present("Dear Customer, you have 10.5GB data valid till 25/09.")
        assertEquals("Data balance", friendly.heading)
        assertNull(friendly.amount)
    }

    @Test
    fun `phone number response is labelled your number`() {
        val friendly = UssdPresenter.present("Your number is 08031234567.")
        assertEquals("Your number", friendly.heading)
    }

    @Test
    fun `failure text gets a negative heading`() {
        val friendly = UssdPresenter.present("Transaction failed: insufficient balance")
        assertEquals("Didn't work", friendly.heading)
    }

    @Test
    fun `unrecognised text has no heading and keeps the message body`() {
        val friendly = UssdPresenter.present("Welcome to self service.\n\n\nChoose an option.")
        assertNull(friendly.heading)
        assertEquals("Welcome to self service.\n\nChoose an option.", friendly.body)
    }
}