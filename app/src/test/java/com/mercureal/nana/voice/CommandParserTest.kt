package com.mercureal.nana.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {

    @Test
    fun fixedPhrasesWinOverRegexRules() {
        assertEquals(Command.Back, CommandParser.parse("press back"))
        assertEquals(Command.Back, CommandParser.parse("Go back."))
        assertEquals(Command.Notifications, CommandParser.parse("open notifications"))
        assertEquals(Command.Recents, CommandParser.parse("open recents"))
        assertEquals(Command.Home, CommandParser.parse("press home"))
    }

    @Test
    fun openAppAndTap() {
        assertEquals(Command.OpenApp("camera"), CommandParser.parse("open camera"))
        assertEquals(Command.OpenApp("whatsapp"), CommandParser.parse("launch whatsapp"))
        assertEquals(Command.TapText("send"), CommandParser.parse("tap send"))
    }

    @Test
    fun callNumberFromDigitsAndWords() {
        assertEquals(Command.CallNumber("08012345678"), CommandParser.parse("call 08012345678"))
        assertEquals(Command.CallNumber("080123"), CommandParser.parse("call zero eight zero one two three"))
        assertEquals(Command.CallNumber("+2348012"), CommandParser.parse("call plus two three four eight zero one two"))
        assertEquals(Command.CallNumber("911"), CommandParser.parse("dial 911"))
    }

    @Test
    fun callWithoutDigitsIsContact() {
        assertEquals(Command.CallContact("mom"), CommandParser.parse("call mom"))
    }

    @Test
    fun screenAndScroll() {
        assertEquals(Command.ReadScreen, CommandParser.parse("read screen"))
        assertEquals(Command.ReadScreen, CommandParser.parse("what's on screen"))
        assertEquals(Command.ScrollDown, CommandParser.parse("scroll down"))
        assertEquals(Command.StopListening, CommandParser.parse("stop listening"))
    }

    @Test
    fun unknownAndEmpty() {
        assertEquals(Command.Unknown("open"), CommandParser.parse("open"))
        assertEquals(Command.Unknown("blah blah"), CommandParser.parse("blah blah"))
    }

    @Test
    fun wakeWordExtraction() {
        assertEquals("open camera", CommandParser.afterWakeWord("Nana, open camera"))
        assertEquals("", CommandParser.afterWakeWord("nana"))
        assertEquals("press back", CommandParser.afterWakeWord("nanna press back"))
        assertEquals("press back", CommandParser.afterWakeWord("na na press back"))
        assertNull(CommandParser.afterWakeWord("open camera"))
        assertNull(CommandParser.afterWakeWord("banana split"))
    }

    @Test
    fun spokenNumberRules() {
        assertNull(CommandParser.spokenNumber("mom"))
        assertNull(CommandParser.spokenNumber("12"))
    }
}
