package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.ChefsNote;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.entities.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ChefsNoteRepository extends JpaRepository<ChefsNote, Long> {
    Optional<ChefsNote> findFirstByUserAndRecipe(User user, Recipe recipe);
}