package com.reajason.noone.core;

import com.reajason.noone.core.client.Client;
import com.reajason.noone.core.profile.Profile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionConfig {
    private Client loaderClient;
    private Client coreClient;
    private String shellType;
    private Profile coreProfile;
}
