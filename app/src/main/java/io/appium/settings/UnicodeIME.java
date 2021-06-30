/*
 *    Copyright 2013 TOYAMA Sumio <jun.nama@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appium.settings;

import android.annotation.SuppressLint;
import android.inputmethodservice.InputMethodService;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * <p>
 * UnicodeIME enables users to input any Unicode character by using only the
 * hardware keyboard. The selection of word candidates is not necessary. <br />
 * Using automated testing tools such as Uiautomator, it is impossible to input
 * non-ASCII characters directly. UnicodeIME helps you to input any
 * characters by using Uiautomator.
 * </p>
 * <p>
 * String that is input from the keyboard, must be encoded in Modified UTF-7
 * (see RFC 3501).
 * </p>
 *
 * @author TOYAMA Sumio
 */
public class UnicodeIME extends InputMethodService {
    private static final String TAG = UnicodeIME.class.getSimpleName();

    @SuppressWarnings("InjectedReferences")
    private static final Charset UTF7_MODIFIED = Charset.forName("x-IMAP-mailbox-name");
    private static final Charset ASCII = Charset.forName("US-ASCII");

    private static final CharsetDecoder UTF7_DECODER = UTF7_MODIFIED.newDecoder();

    /**
     * Special character to shift to Modified BASE64 in modified UTF-7.
     */
    private static final char M_UTF7_SHIFT = '&';

    /**
     * Special character to shift back to US-ASCII in modified UTF-7.
     */
    private static final char M_UTF7_UNSHIFT = '-';

    /**
     * Indicates if current UTF-7 state is Modified BASE64 or not.
     */
    private boolean isShifted = false;
    private long metaState = 0;
    private StringBuilder unicodeString = new StringBuilder();

    /**
     * ADDED BY MO: Editor Action 싦행을 위해 사용된다.
     */
    private EditorInfo editorInfo = null;
    //END

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        Log.i(TAG, "onStartInput");
        super.onStartInput(attribute, restarting);

        if (!restarting) {
            metaState = 0;
            isShifted = false;
        }
        unicodeString = new StringBuilder();
        //ADDED BY MO: Editor Action 싦행을 위해 사용된다.
        this.editorInfo = attribute;
        //END
    }

    @Override
    public void onFinishInput() {
        Log.i(TAG, String.format("onFinishInput: %s", unicodeString));
        super.onFinishInput();
        unicodeString = new StringBuilder();
        //ADDED BY MO: Editor Action 싦행을 위해 사용된다.
        this.editorInfo = null;
        //END
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public boolean onEvaluateInputViewShown() {
        return false;
    }

    /**
     * KEYCODE_ENTER 호출시 키보드에서 수행할 수 있는 Editor Action 이 존재할 경우 실행한다.
     * Available Actions:
     *  - {@link EditorInfo.IME_ACTION_GO}
     *  - {@link EditorInfo.IME_ACTION_SEARCH}
     *  - {@link EditorInfo.IME_ACTION_SEND}
     *  - {@link EditorInfo.IME_ACTION_NEXT}
     *  - {@link EditorInfo.IME_ACTION_DONE}
     *  - {@link EditorInfo.IME_ACTION_PREVIOUS}
     * @param attribute - The attributes of the editor that input is starting in.
     * @return success
     */
    private boolean onKeycodeEnter(EditorInfo attribute) {
        if (attribute == null) {
            return false;
        }
        switch (attribute.imeOptions & (EditorInfo.IME_MASK_ACTION|EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            case EditorInfo.IME_ACTION_GO:
                Log.i(TAG, String.format("performEditorAction=IME_ACTION_GO"));
                return getCurrentInputConnection().performEditorAction(EditorInfo.IME_ACTION_GO);
            case EditorInfo.IME_ACTION_SEARCH:
                Log.i(TAG, String.format("performEditorAction=IME_ACTION_SEARCH"));
                return getCurrentInputConnection().performEditorAction(EditorInfo.IME_ACTION_SEARCH);
            case EditorInfo.IME_ACTION_SEND:
                Log.i(TAG, String.format("performEditorAction=IME_ACTION_SEND"));
                return getCurrentInputConnection().performEditorAction(EditorInfo.IME_ACTION_SEND);
            case EditorInfo.IME_ACTION_NEXT:
                Log.i(TAG, String.format("performEditorAction=IME_ACTION_NEXT"));
                return getCurrentInputConnection().performEditorAction(EditorInfo.IME_ACTION_NEXT);
            case EditorInfo.IME_ACTION_DONE:
                Log.i(TAG, String.format("performEditorAction=IME_ACTION_DONE"));
                return getCurrentInputConnection().performEditorAction(EditorInfo.IME_ACTION_DONE);
            case EditorInfo.IME_ACTION_PREVIOUS:
                Log.i(TAG, String.format("performEditorAction=IME_ACTION_PREVIOUS"));
                return getCurrentInputConnection().performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i(TAG, String.format("onKeyDown (keyCode='%s', event.keyCode='%s', metaState='%s')",
                keyCode, event.getKeyCode(), event.getMetaState()));
        //ADDED BY MO: KEYCODE_CLEAR 를 사용하여 input box의 기 입력된 내용을 삭제한다.
        // 다른 방법으로는, 아래와 같이 clear할 수 있다.
        //   1) adb shell input keyevent KEYCODE_MOVE_END
        //   2) adb shell input keyevent --longpress $(printf 'KEYCODE_DEL %.0s' {1..250})
        if (keyCode == KeyEvent.KEYCODE_CLEAR) {
            Log.i(TAG, String.format("keyCode=KEYCODE_CLEAR"));
            this.clear();
            return true;
        }
        //END

        //ADDED BY MO: KEYCODE_ENTER 호출시 키보드에서 수행할 수 있는 Editor Action 이 존재할 경우 실행한다.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            Log.i(TAG, String.format("keyCode=KEYCODE_ENTER"));
            if (this.onKeycodeEnter(this.editorInfo)) {
                return true;
            }
        }
        //END

        int c = getUnicodeChar(keyCode, event);
        if (c == 0) {
            return super.onKeyDown(keyCode, event);
        }

        if (!isShifted) {
            if (c == M_UTF7_SHIFT) {
                shift();
                return true;
            }
            if (isAsciiPrintable(c)) {
                commitChar(c);
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        if (c == M_UTF7_UNSHIFT) {
            unshift();
        } else {
            appendChar(c);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i(TAG, String.format("onKeyUp (keyCode='%s', event.keyCode='%s', metaState='%s')",
                keyCode, event.getKeyCode(), event.getMetaState()));
        metaState = MetaKeyKeyListener.handleKeyUp(metaState, keyCode, event);
        return super.onKeyUp(keyCode, event);
    }

    /**
     * clear all
     */
    private void clear() {
        getCurrentInputConnection().deleteSurroundingText(10000, 10000);
    }

    private void shift() {
        isShifted = true;
        unicodeString = new StringBuilder();
        appendChar(M_UTF7_SHIFT);
    }

    private void unshift() {
        isShifted = false;
        unicodeString.append(M_UTF7_UNSHIFT);
        String decoded = decodeUtf7(unicodeString.toString());
        getCurrentInputConnection().commitText(decoded, 1);
        unicodeString = new StringBuilder();
    }

    private int getUnicodeChar(int keyCode, KeyEvent event) {
        metaState = MetaKeyKeyListener.handleKeyDown(metaState, keyCode, event);
        int c = event.getUnicodeChar(event.getMetaState());
        metaState = MetaKeyKeyListener.adjustMetaAfterKeypress(metaState);
        return c;
    }

    private void commitChar(int c) {
        getCurrentInputConnection().commitText(String.valueOf((char) c), 1);
    }

    private void appendChar(int c) {
        unicodeString.append((char) c);
    }

    private static String decodeUtf7(String encStr) {
        ByteBuffer encoded = ByteBuffer.wrap(encStr.getBytes(ASCII));
        String decoded;
        try {
            CharBuffer buf = UTF7_DECODER.decode(encoded);
            decoded = buf.toString();
        } catch (CharacterCodingException e) {
            Log.e(TAG, e.getMessage());
            decoded = encStr;
        }
        return decoded;
    }

    private static boolean isAsciiPrintable(int c) {
        return c >= 0x20 && c <= 0x7E;
    }

}
