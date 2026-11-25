package com.waterrepair.topology;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopologyVersionRepository extends JpaRepository<TopologyVersion, Long> {
    Optional<TopologyVersion> findByVersionCode(String versionCode);
    Optional<TopologyVersion> findFirstByStatusOrderByVersionNoDesc(String status);
    List<TopologyVersion> findAllByOrderByVersionNoAscIdAsc();
}
