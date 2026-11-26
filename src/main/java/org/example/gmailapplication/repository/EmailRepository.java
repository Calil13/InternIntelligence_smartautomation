package org.example.gmailapplication.repository;

import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {

    Page<Email> findByUserId(Long userId, Pageable pageable);

    List<Email> findByUserId(Long userId);

    Page<Email> findByUserIdAndIsStarredTrue(Long userId, Pageable pageable);

    Page<Email> findByUserIdAndFolder(Long userId, EmailFolder folder, Pageable pageable);

    Page<Email> findByUserIdAndCategory(Long userId, EmailCategory category, Pageable pageable);

    List<Email> findByUserIdAndCategory(Long userId, EmailCategory category);

    @Query("SELECT e FROM Email e JOIN e.labels l WHERE e.user.id = :userId AND LOWER(l) = LOWER(:label)")
    Page<Email> findByUserIdAndLabel(@Param("userId") Long userId, @Param("label") String label, Pageable pageable);

    @Query("SELECT l, COUNT(e) FROM Email e JOIN e.labels l WHERE e.user.id = :userId GROUP BY l")
    List<Object[]> countLabelsByUserId(@Param("userId") Long userId);

    long countByUserIdAndFolderAndIsReadFalse(Long userId, EmailFolder folder);

    long countByUserIdAndIsStarredTrue(Long userId);

    long countByUserIdAndFolder(Long userId, EmailFolder folder);

    long countByUserIdAndCategory(Long userId, EmailCategory category);
}
