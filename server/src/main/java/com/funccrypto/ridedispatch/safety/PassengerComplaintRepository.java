package com.funccrypto.ridedispatch.safety;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerComplaintRepository extends JpaRepository<PassengerComplaintEntity, Long> {

    List<PassengerComplaintEntity> findAllByOrderByCreatedAtDesc();

    List<PassengerComplaintEntity> findAllByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    java.util.Optional<PassengerComplaintEntity> findByComplaintNo(String complaintNo);
}
