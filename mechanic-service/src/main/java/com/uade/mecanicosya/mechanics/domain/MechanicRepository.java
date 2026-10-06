package com.uade.mecanicosya.mechanics.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MechanicRepository extends JpaRepository<Mechanic, UUID> {

    @Query("""
            select distinct mechanic
            from Mechanic mechanic
            join mechanic.skills skill
            where mechanic.available = true
              and upper(skill) = upper(:skill)
            """)
    List<Mechanic> findAvailableBySkill(@Param("skill") String skill);

    @Query("""
            select distinct mechanic
            from Mechanic mechanic
            join mechanic.skills skill
            join mechanic.vehicleTypes vehicleType
            where mechanic.available = true
              and upper(skill) = upper(:skill)
              and vehicleType = :vehicleType
            """)
    List<Mechanic> findAvailableBySkillAndVehicleType(
            @Param("skill") String skill,
            @Param("vehicleType") VehicleType vehicleType
    );
}

