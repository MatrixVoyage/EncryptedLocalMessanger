import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class EncryptedMultiServerTest {

    @Test
    void dispatchToRecipientsSkipsSenderAndReachesOthers() {
        ClientHandler sender = Mockito.mock(ClientHandler.class);
        ClientHandler recipientA = Mockito.mock(ClientHandler.class);
        ClientHandler recipientB = Mockito.mock(ClientHandler.class);
        List<ClientHandler> clients = Arrays.asList(sender, recipientA, recipientB);

        char[] password = "secret".toCharArray();
        String payload = "[client] hello";

        EncryptedMultiServer.dispatchToRecipients(clients, sender, payload, password);

    verify(recipientA).sendAsync(eq(payload), same(password));
    verify(recipientB).sendAsync(eq(payload), same(password));
        verify(sender, never()).sendAsync(anyString(), any());
        verifyNoMoreInteractions(recipientA, recipientB, sender);
    }

    @Test
    void dispatchToRecipientsHandlesSingleClientGracefully() {
        ClientHandler loneClient = Mockito.mock(ClientHandler.class);
        char[] password = "secret".toCharArray();

        EncryptedMultiServer.dispatchToRecipients(List.of(loneClient), loneClient, "payload", password);

    verify(loneClient, never()).sendAsync(anyString(), any());
    }
}
