package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.user.dto.UserIpWhitelistCreateRequest;
import com.reajason.noone.server.admin.user.dto.UserIpWhitelistResponse;
import com.reajason.noone.server.api.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserIpWhitelistServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIpWhitelistRepository userIpWhitelistRepository;

    @InjectMocks
    private UserIpWhitelistService userIpWhitelistService;

    @Captor
    private ArgumentCaptor<Iterable<UserIpWhitelist>> whitelistCaptor;

    @Test
    void shouldAddWhitelistEntry() {
        User user = buildUser(1L, "alice");
        UserIpWhitelistCreateRequest request = new UserIpWhitelistCreateRequest();
        request.setIpAddress("10.0.0.1");

        UserIpWhitelist saved = buildWhitelist(11L, user, "10.0.0.1");
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userIpWhitelistRepository.existsByUserIdAndIpAddress(1L, "10.0.0.1")).thenReturn(false);
        when(userIpWhitelistRepository.save(any(UserIpWhitelist.class))).thenReturn(saved);

        UserIpWhitelistResponse response = userIpWhitelistService.add(1L, request);

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getIpAddress()).isEqualTo("10.0.0.1");
        verify(userIpWhitelistRepository).save(any(UserIpWhitelist.class));
    }

    @Test
    void shouldNormalizeIpv6AddressWhenAddingWhitelistEntry() {
        User user = buildUser(1L, "alice");
        UserIpWhitelistCreateRequest request = new UserIpWhitelistCreateRequest();
        request.setIpAddress("2001:db8::1");

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userIpWhitelistRepository.existsByUserIdAndIpAddress(1L, "2001:db8:0:0:0:0:0:1")).thenReturn(false);
        when(userIpWhitelistRepository.save(any(UserIpWhitelist.class))).thenAnswer(invocation -> {
            UserIpWhitelist entry = invocation.getArgument(0);
            entry.setId(12L);
            entry.setCreatedAt(LocalDateTime.of(2025, 1, 1, 12, 0));
            return entry;
        });

        UserIpWhitelistResponse response = userIpWhitelistService.add(1L, request);

        assertThat(response.getIpAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
        verify(userIpWhitelistRepository).existsByUserIdAndIpAddress(1L, "2001:db8:0:0:0:0:0:1");
    }

    @Test
    void shouldRejectInvalidIpAddress() {
        User user = buildUser(1L, "alice");
        UserIpWhitelistCreateRequest request = new UserIpWhitelistCreateRequest();
        request.setIpAddress("10.0.0.*");

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userIpWhitelistService.add(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("IP地址不合法：10.0.0.*");

        verifyNoInteractions(userIpWhitelistRepository);
    }

    @Test
    void shouldRejectDuplicateIpAddressForSameUser() {
        User user = buildUser(1L, "alice");
        UserIpWhitelistCreateRequest request = new UserIpWhitelistCreateRequest();
        request.setIpAddress("10.0.0.1");

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userIpWhitelistRepository.existsByUserIdAndIpAddress(1L, "10.0.0.1")).thenReturn(true);

        assertThatThrownBy(() -> userIpWhitelistService.add(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("IP地址已存在：10.0.0.1");

        verify(userIpWhitelistRepository, never()).save(any());
    }

    @Test
    void shouldTranslateUniqueConstraintViolationWhenAddingDuplicateIp() {
        User user = buildUser(1L, "alice");
        UserIpWhitelistCreateRequest request = new UserIpWhitelistCreateRequest();
        request.setIpAddress("10.0.0.1");

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userIpWhitelistRepository.existsByUserIdAndIpAddress(1L, "10.0.0.1")).thenReturn(false);
        when(userIpWhitelistRepository.save(any(UserIpWhitelist.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> userIpWhitelistService.add(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("IP地址已存在：10.0.0.1");
    }

    @Test
    void shouldReplaceWhitelistWithDeduplicatedEntries() {
        User user = buildUser(1L, "alice");
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userIpWhitelistRepository.saveAll(whitelistCaptor.capture())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Iterable<UserIpWhitelist> iterable = invocation.getArgument(0);
            long[] nextId = {1L};
            for (UserIpWhitelist entry : iterable) {
                entry.setId(nextId[0]++);
                entry.setCreatedAt(LocalDateTime.of(2025, 1, 1, 12, 0));
            }
            return iterable;
        });

        List<UserIpWhitelistResponse> response = userIpWhitelistService.replace(1L, List.of(
                "10.0.0.1",
                "10.0.0.1",
                "10.0.0.2"));

        assertThat(response).hasSize(2);
        assertThat(response).extracting(UserIpWhitelistResponse::getIpAddress)
                .containsExactly("10.0.0.1", "10.0.0.2");
        verify(userIpWhitelistRepository).deleteByUserId(1L);
        assertThat(toList(whitelistCaptor.getValue()))
                .extracting(UserIpWhitelist::getIpAddress)
                .containsExactly("10.0.0.1", "10.0.0.2");
    }

    @Test
    void shouldThrowWhenDeletingEntryBelongingToAnotherUser() {
        User user = buildUser(1L, "alice");
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userIpWhitelistRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userIpWhitelistService.delete(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("用户 IP 白名单不存在：99");

        verify(userIpWhitelistRepository, never()).delete(any());
    }

    @Test
    void shouldThrowWhenUserDoesNotExistOnList() {
        when(userRepository.findByIdAndDeletedFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userIpWhitelistService.list(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("用户不存在：404");
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDeleted(Boolean.FALSE);
        return user;
    }

    private UserIpWhitelist buildWhitelist(Long id, User user, String ipAddress) {
        UserIpWhitelist whitelist = new UserIpWhitelist();
        whitelist.setId(id);
        whitelist.setUser(user);
        whitelist.setIpAddress(ipAddress);
        whitelist.setCreatedAt(LocalDateTime.of(2025, 1, 1, 12, 0));
        return whitelist;
    }

    private List<UserIpWhitelist> toList(Iterable<UserIpWhitelist> iterable) {
        return iterable instanceof List<UserIpWhitelist> list ? list : java.util.stream.StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
