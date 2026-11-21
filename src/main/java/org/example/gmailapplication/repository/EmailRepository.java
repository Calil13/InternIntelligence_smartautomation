package org.example.gmailapplication.repository;

import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.enums.EmailFolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {

    Page<Email> findByUserId(Long userId, Pageable pageable);

    Page<Email> findByUserIdAndIsStarredTrue(Long userId, Pageable pageable);

    Page<Email> findByUserIdAndFolder(Long userId, EmailFolder folder, Pageable pageable);

    long countByUserIdAndFolderAndIsReadFalse(Long userId, EmailFolder folder);

    long countByUserIdAndIsStarredTrue(Long userId);

    long countByUserIdAndFolder(Long userId, EmailFolder folder);
}
