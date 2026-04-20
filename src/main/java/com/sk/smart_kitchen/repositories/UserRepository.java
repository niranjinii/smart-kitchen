package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Spring Data JPA automatically writes the SQL query for this just by reading the method name!
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    
}
