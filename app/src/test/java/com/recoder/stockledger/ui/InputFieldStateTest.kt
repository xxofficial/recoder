package com.recoder.stockledger.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class InputFieldStateTest {

    @Test
    fun testUserInputFlow() {
        val state = InputFieldState("")
        var currentText = ""
        
        // 1. User types "L"
        state.updateFromUser(TextFieldValue("L", TextRange(1))) { currentText = it }
        assertEquals("L", currentText)
        assertEquals("L", state.textFieldValue.text)
        
        // State updates with "L" (no change)
        state.updateFromState("L")
        assertEquals("L", state.textFieldValue.text)
        
        // 2. User types "LW"
        state.updateFromUser(TextFieldValue("LW", TextRange(2))) { currentText = it }
        assertEquals("LW", currentText)
        assertEquals("LW", state.textFieldValue.text)
        
        // 3. User types "LWL"
        state.updateFromUser(TextFieldValue("LWL", TextRange(3))) { currentText = it }
        assertEquals("LWL", currentText)
        assertEquals("LWL", state.textFieldValue.text)
        
        // 4. User types "LWLG"
        state.updateFromUser(TextFieldValue("LWLG", TextRange(4))) { currentText = it }
        assertEquals("LWLG", currentText)
        assertEquals("LWLG", state.textFieldValue.text)
    }

    @Test
    fun testIgnoreStaleStateUpdate() {
        val state = InputFieldState("")
        var currentText = ""
        
        // User types "L", "LW", "LWL", "LWLG"
        state.updateFromUser(TextFieldValue("L", TextRange(1))) { currentText = it }
        state.updateFromUser(TextFieldValue("LW", TextRange(2))) { currentText = it }
        state.updateFromUser(TextFieldValue("LWL", TextRange(3))) { currentText = it }
        state.updateFromUser(TextFieldValue("LWLG", TextRange(4))) { currentText = it }
        
        // A stale/delayed update "LWL" comes from the state
        state.updateFromState("LWL")
        // It should be ignored, because "LWL" is in the user input history
        assertEquals("LWLG", state.textFieldValue.text)
    }

    @Test
    fun testProgrammaticUpdateAndIgnoreStaleImeReversion() {
        val state = InputFieldState("")
        var currentText = "LWLG"
        
        // Set up the state as if user typed "LWLG"
        state.updateFromUser(TextFieldValue("L", TextRange(1))) { currentText = it }
        state.updateFromUser(TextFieldValue("LW", TextRange(2))) { currentText = it }
        state.updateFromUser(TextFieldValue("LWL", TextRange(3))) { currentText = it }
        state.updateFromUser(TextFieldValue("LWLG", TextRange(4))) { currentText = it }
        
        // Programmatic update from state (autocomplete selection)
        state.updateFromState("Lightwave Logic Inc LWLG")
        assertEquals("Lightwave Logic Inc LWLG", state.textFieldValue.text)
        
        // Stale IME update arrives trying to revert to the old value "LWLG"
        state.updateFromUser(TextFieldValue("LWLG", TextRange(4))) { currentText = it }
        
        // The stale update should be ignored! The field value should remain the programmatic one.
        assertEquals("Lightwave Logic Inc LWLG", state.textFieldValue.text)
        
        // Now user edits normally (e.g. backspace to "Lightwave Logic Inc LWL")
        state.updateFromUser(TextFieldValue("Lightwave Logic Inc LWL", TextRange(24))) { currentText = it }
        assertEquals("Lightwave Logic Inc LWL", currentText)
        assertEquals("Lightwave Logic Inc LWL", state.textFieldValue.text)
    }
}
