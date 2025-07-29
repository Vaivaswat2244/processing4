package processing.core;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import processing.event.KeyEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PAppletKeyEventTest {

    private static final int SHIFT_MASK = 1;
    private static final int CTRL_MASK = 2;
    private static final int ALT_MASK = 4;

    private PApplet applet;

    @Before
    public void setup() {
        applet = new PApplet();
    }

    @Test
    public void testSingleKeyPressAndRelease() {
        KeyEvent pressEvent = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, 'a', 65, false);
        applet.handleKeyEvent(pressEvent);
        Assert.assertEquals(1, applet.pressedKeys.size());

        KeyEvent releaseEvent = new KeyEvent(null, 0L, KeyEvent.RELEASE, 0, 'a', 65, false);
        applet.handleKeyEvent(releaseEvent);
        Assert.assertEquals(0, applet.pressedKeys.size());
        Assert.assertFalse(applet.keyPressed);
    }

    @Test
    public void testShiftAndLetterSequence() {
        KeyEvent pressA = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, 'a', 65, false);
        applet.handleKeyEvent(pressA);

        KeyEvent pressShift = new KeyEvent(null, 0L, KeyEvent.PRESS, SHIFT_MASK, 'A', 16, false);
        applet.handleKeyEvent(pressShift);

        KeyEvent releaseA = new KeyEvent(null, 0L, KeyEvent.RELEASE, SHIFT_MASK, 'A', 65, false);
        applet.handleKeyEvent(releaseA);

        KeyEvent releaseShift = new KeyEvent(null, 0L, KeyEvent.RELEASE, 0, 'A', 16, false);
        applet.handleKeyEvent(releaseShift);

        Assert.assertFalse("keyPressed should be false after all keys released", applet.keyPressed);
        Assert.assertTrue("pressedKeys should be empty", applet.pressedKeys.isEmpty());
    }

    @Test
    public void testControlAndLetterSequence() {
        KeyEvent pressCtrl = new KeyEvent(null, 0L, KeyEvent.PRESS, CTRL_MASK, '\0', 17, false);
        applet.handleKeyEvent(pressCtrl);

        KeyEvent pressC = new KeyEvent(null, 0L, KeyEvent.PRESS, CTRL_MASK, (char)3, 67, false);
        applet.handleKeyEvent(pressC);

        KeyEvent releaseC = new KeyEvent(null, 0L, KeyEvent.RELEASE, CTRL_MASK, 'c', 67, false);
        applet.handleKeyEvent(releaseC);

        KeyEvent releaseCtrl = new KeyEvent(null, 0L, KeyEvent.RELEASE, 0, '\0', 17, false);
        applet.handleKeyEvent(releaseCtrl);

        Assert.assertFalse("keyPressed should be false after all keys released", applet.keyPressed);
        Assert.assertTrue("pressedKeys should be empty", applet.pressedKeys.isEmpty());
    }

    @Test
    public void testAltAndLetterSequence() {
        KeyEvent pressV = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, 'v', 86, false);
        applet.handleKeyEvent(pressV);

        KeyEvent pressAlt = new KeyEvent(null, 0L, KeyEvent.PRESS, ALT_MASK, 'v', 18, false);
        applet.handleKeyEvent(pressAlt);

        KeyEvent releaseV = new KeyEvent(null, 0L, KeyEvent.RELEASE, ALT_MASK, 'v', 86, false);
        applet.handleKeyEvent(releaseV);

        KeyEvent releaseAlt = new KeyEvent(null, 0L, KeyEvent.RELEASE, 0, 'v', 18, false);
        applet.handleKeyEvent(releaseAlt);

        Assert.assertFalse("keyPressed should be false after all keys released", applet.keyPressed);
        Assert.assertTrue("pressedKeys should be empty", applet.pressedKeys.isEmpty());
    }

    @Test
    public void testKeyRepeat() {
        applet.keyRepeatEnabled = false;

        KeyEvent pressR = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, 'r', 82, false);
        applet.handleKeyEvent(pressR);

        KeyEvent repeatR = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, 'r', 82, true);
        applet.handleKeyEvent(repeatR);

        Assert.assertTrue("keyPressed should be true after key press", applet.keyPressed);
        Assert.assertEquals("pressedKeys should have 1 entry", 1, applet.pressedKeys.size());

        KeyEvent releaseR = new KeyEvent(null, 0L, KeyEvent.RELEASE, 0, 'r', 82, false);
        applet.handleKeyEvent(releaseR);

        Assert.assertFalse("keyPressed should be false after key release", applet.keyPressed);
        Assert.assertEquals("pressedKeys should be empty", true, applet.pressedKeys.isEmpty());
    }

    @Test
    public void testKeyRepeatEnabled() {
        applet.keyRepeatEnabled = true;

        KeyEvent pressT = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, 't', 84, false);
        applet.handleKeyEvent(pressT);

        KeyEvent repeatT = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, 't', 84, true);
        applet.handleKeyEvent(repeatT);

        Assert.assertTrue("keyPressed should be true with key repeat enabled", applet.keyPressed);
        Assert.assertEquals("pressedKeys should have 1 entry", 1, applet.pressedKeys.size());

        KeyEvent releaseT = new KeyEvent(null, 0L, KeyEvent.RELEASE, 0, 't', 84, false);
        applet.handleKeyEvent(releaseT);

        Assert.assertFalse("keyPressed should be false after key release", applet.keyPressed);
        Assert.assertEquals("pressedKeys should be empty", true, applet.pressedKeys.isEmpty());
    }

    @Test
    public void testKeyFocusLost() {
        KeyEvent pressF = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, 'f', 70, false);
        applet.handleKeyEvent(pressF);

        Assert.assertTrue("keyPressed should be true after key press", applet.keyPressed);
        Assert.assertEquals("pressedKeys should have 1 entry", 1, applet.pressedKeys.size());

        applet.focusLost();

        Assert.assertFalse("keyPressed should be false after focus lost", applet.keyPressed);
        Assert.assertEquals("pressedKeys should be empty after focus lost", true, applet.pressedKeys.isEmpty());
    }

    @Test
    public void testShiftAndPageUpKeyCodesAreDifferent() {
        final int VK_SHIFT   = java.awt.event.KeyEvent.VK_SHIFT;
        final int VK_PAGE_UP = java.awt.event.KeyEvent.VK_PAGE_UP;    

        long shiftHash  = ((long)VK_SHIFT   << Character.SIZE);
        long pageUpHash = ((long)VK_PAGE_UP << Character.SIZE);    

        KeyEvent shiftPressEvent = new KeyEvent(null, 0L, KeyEvent.PRESS, SHIFT_MASK, '\0', VK_SHIFT, false);
        applet.handleKeyEvent(shiftPressEvent);

        assertTrue("keyPressed must be true", applet.keyPressed);
        assertTrue("SHIFT should be in pressedKeys", applet.pressedKeys.contains(shiftHash));

        KeyEvent pageUpPressEvent = new KeyEvent(null, 0L, KeyEvent.PRESS, 0, '\0', VK_PAGE_UP, false);
        applet.handleKeyEvent(pageUpPressEvent);

        assertEquals("pressedKeys must contain exactly two keys", 2, applet.pressedKeys.size());
        assertTrue("PAGE_UP should be in pressedKeys", applet.pressedKeys.contains(pageUpHash));

        KeyEvent shiftRelease = new KeyEvent(null, 0L, KeyEvent.RELEASE, 0, '\0', VK_SHIFT, false);
        applet.handleKeyEvent(shiftRelease);
        assertFalse("SHIFT should have been removed", applet.pressedKeys.contains(shiftHash));
        assertTrue ("PAGE_UP should still be down", applet.pressedKeys.contains(pageUpHash));
        assertTrue ("keyPressed must still be true", applet.keyPressed);
        assertEquals("pressedKeys must now have one key", 1, applet.pressedKeys.size());

        KeyEvent pageUpRelease = new KeyEvent(null, 0L, KeyEvent.RELEASE, 0, '\0', VK_PAGE_UP, false);
        applet.handleKeyEvent(pageUpRelease);
        assertTrue ("pressedKeys must now be empty", applet.pressedKeys.isEmpty());
        assertFalse("keyPressed must be false", applet.keyPressed);
    }
}
