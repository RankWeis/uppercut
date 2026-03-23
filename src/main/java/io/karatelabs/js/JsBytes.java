package io.karatelabs.js;

import java.util.Map;

public class JsBytes extends JsObject {

    final byte[] bytes;

    public JsBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    Map<String, Object> initPrototype() {
        Map<String, Object> prototype = super.initPrototype();
        prototype.put("length", new Property(() -> bytes.length));
        return prototype;
    }

}
