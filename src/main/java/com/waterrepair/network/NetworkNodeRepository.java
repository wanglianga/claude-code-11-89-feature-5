package com.waterrepair.network;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NetworkNodeRepository extends JpaRepository<NetworkNode, String> {
}
