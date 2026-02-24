package com.reajason.noone.noone.core;

import com.reajason.noone.Constants;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.client.Client;
import com.reajason.noone.core.exception.RequestSerializeException;
import com.reajason.noone.core.exception.ResponseBusinessException;
import com.reajason.noone.core.exception.ResponseDecodeException;
import com.reajason.noone.core.exception.RequestSendException;
import com.reajason.noone.core.exception.ShellRequestException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShellConnectionSendRequestTest {

    @Test
    void shouldThrowResponseDecodeExceptionWhenClientReturnsNull() {
        TestClient client = new TestClient();
        client.nextResponse = null;

        TestShellConnection conn = new TestShellConnection(client);
        assertThrows(ResponseDecodeException.class, () -> conn.sendRequestPublic(Map.of("action", "status")));
    }

    @Test
    void shouldThrowRequestSerializeExceptionWhenSerializeThrows() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.serializeException = new RuntimeException("serialize boom");

        RequestSerializeException exception = assertThrows(
                RequestSerializeException.class,
                () -> conn.sendRequestPublic(Map.of("action", "status"))
        );
        assertTrue(exception.getMessage().contains("serialize"));
    }

    @Test
    void shouldThrowShellRequestExceptionWhenClientThrows() {
        TestClient client = new TestClient();
        client.sendException = new RuntimeException("send boom");

        TestShellConnection conn = new TestShellConnection(client);
        ShellRequestException exception = assertThrows(
                ShellRequestException.class,
                () -> conn.sendRequestPublic(Map.of("action", "status"))
        );
        assertTrue(exception.getMessage().contains("send shell request"));
    }

    @Test
    void shouldThrowResponseDecodeExceptionWhenDeserializeThrows() {
        TestClient client = new TestClient();
        client.nextResponse = new byte[]{1, 2, 3};

        TestShellConnection conn = new TestShellConnection(client);
        conn.deserializeException = new RuntimeException("deserialize boom");

        ResponseDecodeException exception = assertThrows(
                ResponseDecodeException.class,
                () -> conn.sendRequestPublic(Map.of("action", "status"))
        );
        assertTrue(exception.getMessage().contains("deserialize"));
    }

    @Test
    void shouldPropagateShellCommunicationExceptionFromClientSend() {
        TestClient client = new TestClient();
        RequestSendException sendException = new RequestSendException("send failed", 1, new RuntimeException("io"));
        client.sendException = sendException;

        TestShellConnection conn = new TestShellConnection(client);
        RequestSendException thrown = assertThrows(
                RequestSendException.class,
                () -> conn.sendRequestPublic(Map.of("action", "status"))
        );
        assertSame(sendException, thrown);
    }

    @Test
    void shouldThrowResponseBusinessExceptionWhenStatusResponseIsFailure() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.nextDeserializedResponse = Map.of(
                Constants.CODE, Constants.FAILURE,
                Constants.ERROR, "status failed"
        );

        ResponseBusinessException exception = assertThrows(ResponseBusinessException.class, conn::test);
        assertTrue(exception.getMessage().contains("status failed"));
    }

    @Test
    void shouldThrowResponseBusinessExceptionWhenLoadPluginResponseIsFailure() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.nextDeserializedResponse = Map.of(
                Constants.CODE, Constants.FAILURE,
                Constants.ERROR, "load failed"
        );

        ResponseBusinessException exception = assertThrows(
                ResponseBusinessException.class,
                () -> conn.loadPlugin("system-info", new byte[]{1})
        );
        assertTrue(exception.getMessage().contains("load failed"));
    }

    @Test
    void shouldThrowResponseDecodeExceptionWhenStatusResponseCodeIsMissing() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.nextDeserializedResponse = Map.of(Constants.ERROR, "missing code");

        ResponseDecodeException exception = assertThrows(ResponseDecodeException.class, conn::test);
        assertTrue(exception.getMessage().contains("Missing or invalid code"));
    }

    @Test
    void shouldThrowResponseDecodeExceptionWhenLoadPluginResponseCodeIsUnexpected() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.nextDeserializedResponse = Map.of(Constants.CODE, 99);

        ResponseDecodeException exception = assertThrows(
                ResponseDecodeException.class,
                () -> conn.loadPlugin("system-info", new byte[]{1})
        );
        assertTrue(exception.getMessage().contains("Unexpected load response code"));
    }

    @Test
    void shouldReturnTrueAndCachePluginsWhenStatusResponseIsSuccess() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.nextDeserializedResponse = Map.of(
                Constants.CODE, Constants.SUCCESS,
                Constants.PLUGIN_CACHES, java.util.Set.of("a", "b")
        );

        assertTrue(conn.test());
        assertFalse(conn.needLoadPlugin("a"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNormalizeCommandExecuteCdArgsBeforeSend() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.nextDeserializedResponse = Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, "ok");

        Map<String, Object> response = conn.runPlugin(
                "command-execute",
                Map.of("cmd", "cd ..", "cwd", "/tmp/work", "charset", "GBK")
        );

        assertEquals(Constants.SUCCESS, response.get(Constants.CODE));
        Map<String, Object> sentArgs = (Map<String, Object>) conn.lastSerializedRequest.get(Constants.ARGS);
        assertEquals("cd", sentArgs.get("op"));
        assertEquals("..", sentArgs.get("cdTarget"));
        assertEquals("/tmp/work", sentArgs.get("cwd"));
        assertEquals("GBK", sentArgs.get("charset"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNormalizeCommandExecuteExecArgsBeforeSend() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.nextDeserializedResponse = Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, "ok");

        Map<String, Object> response = conn.runPlugin(
                "command-execute",
                Map.of(
                        "cmd", "echo ok",
                        "cwd", "/tmp/work",
                        "charset", "GB18030",
                        "commandTemplate", Map.of(
                                "executable", "/bin/sh",
                                "args", List.of("-c", "{{cmd}} in {{cwd}}"),
                                "env", Map.of("NOONE_CMD", "{{cmd}}", "NOONE_CWD", "{{cwd}}")
                        )
                )
        );

        assertEquals(Constants.SUCCESS, response.get(Constants.CODE));
        Map<String, Object> sentArgs = (Map<String, Object>) conn.lastSerializedRequest.get(Constants.ARGS);
        assertEquals("exec", sentArgs.get("op"));
        assertEquals("/bin/sh", sentArgs.get("executable"));
        assertEquals(List.of("-c", "echo ok in /tmp/work"), sentArgs.get("argv"));
        assertEquals(Map.of("NOONE_CMD", "echo ok", "NOONE_CWD", "/tmp/work"), sentArgs.get("env"));
        assertEquals("/tmp/work", sentArgs.get("cwd"));
        assertEquals("GB18030", sentArgs.get("charset"));
    }

    @Test
    void shouldReturnFailureWithoutSendWhenCommandExecuteCmdMissing() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);

        Map<String, Object> response = conn.runPlugin("command-execute", Map.of());

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("cmd is required", response.get(Constants.ERROR));
        assertEquals(0, client.sendCalls);
    }

    @Test
    void shouldReturnFailureWithoutSendWhenExecutableMissing() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);

        Map<String, Object> response = conn.runPlugin(
                "command-execute",
                Map.of("cmd", "whoami", "commandTemplate", Map.of("args", List.of("{{cmd}}")))
        );

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("commandTemplate.executable is required", response.get(Constants.ERROR));
        assertEquals(0, client.sendCalls);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldKeepNonCommandExecuteArgsUnchanged() {
        TestClient client = new TestClient();
        TestShellConnection conn = new TestShellConnection(client);
        conn.nextDeserializedResponse = Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, "ok");
        Map<String, Object> args = Map.of("key", "value");

        conn.runPlugin("system-info", args);

        Map<String, Object> sentArgs = (Map<String, Object>) conn.lastSerializedRequest.get(Constants.ARGS);
        assertEquals(args, sentArgs);
    }

    private static final class TestShellConnection extends ShellConnection {
        RuntimeException serializeException;
        RuntimeException deserializeException;
        Map<String, Object> nextDeserializedResponse = Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, "ok");
        Map<String, Object> lastSerializedRequest = new HashMap<>();

        private TestShellConnection(Client client) {
            super(client);
        }

        Map<String, Object> sendRequestPublic(Map<String, Object> requestMap) {
            return sendRequest(requestMap);
        }

        @Override
        public void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap) {
            requestMap.put(Constants.PLUGIN_CODE, pluginCodeBytes);
        }

        @Override
        public byte[] serialize(Map<String, Object> map) {
            if (serializeException != null) {
                throw serializeException;
            }
            lastSerializedRequest = new HashMap<>(map);
            return new byte[]{0};
        }

        @Override
        public Map<String, Object> deserialize(byte[] data) {
            if (deserializeException != null) {
                throw deserializeException;
            }
            return nextDeserializedResponse;
        }
    }

    private static final class TestClient implements Client {
        byte[] nextResponse = new byte[]{1};
        RuntimeException sendException;
        int sendCalls = 0;

        @Override
        public boolean connect() {
            return true;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public byte[] send(String payload) {
            return send(payload != null ? payload.getBytes() : new byte[0]);
        }

        @Override
        public byte[] send(byte[] payload) {
            if (sendException != null) {
                throw sendException;
            }
            sendCalls++;
            return nextResponse;
        }

        @Override
        public String getUrl() {
            return "test://";
        }

        @Override
        public void setUrl(String url) {
        }
    }
}
