package com.assettracker.assignmentservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.entity.HolderType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AssignmentRepositoryTest {

  @Autowired AssignmentRepository repository;

  @Test
  void findsTheOpenAssignmentForAnAssetAndNotTheReturnedOne() {
    Assignment past = new Assignment(1L, 40L, HolderType.PERSON, 7L, "tech", null);
    past.markReturned("tech");
    Assignment current = new Assignment(1L, 40L, HolderType.PERSON, 9L, "tech", null);
    repository.saveAll(java.util.List.of(past, current));

    Optional<Assignment> open = repository.findByAssetIdAndReturnedAtIsNull(40L);
    assertThat(open).isPresent();
    assertThat(open.get().getHolderId()).isEqualTo(9L);
  }

  @Test
  void openAssignmentsForAPersonExcludeReturnedOnes() {
    Assignment a = new Assignment(1L, 40L, HolderType.PERSON, 7L, "tech", null);
    Assignment b = new Assignment(1L, 41L, HolderType.PERSON, 7L, "tech", null);
    b.markReturned("tech");
    repository.saveAll(java.util.List.of(a, b));

    assertThat(repository.findByHolderTypeAndHolderIdAndReturnedAtIsNull(HolderType.PERSON, 7L))
        .extracting(Assignment::getAssetId)
        .containsExactly(40L);
  }

  @Test
  void historyForAnAssetIsNewestFirst() throws InterruptedException {
    Assignment older = new Assignment(1L, 40L, HolderType.PERSON, 7L, "tech", null);
    older.markReturned("tech");
    repository.saveAndFlush(older);

    Thread.sleep(10); // distinct checkedOutAt so DESC ordering is deterministic
    Assignment newer = new Assignment(1L, 40L, HolderType.LOCATION, 3L, "tech", null);
    repository.saveAndFlush(newer);

    assertThat(repository.findByAssetIdOrderByCheckedOutAtDesc(40L))
        .extracting(Assignment::getHolderType)
        .containsExactly(HolderType.LOCATION, HolderType.PERSON);
  }
}
