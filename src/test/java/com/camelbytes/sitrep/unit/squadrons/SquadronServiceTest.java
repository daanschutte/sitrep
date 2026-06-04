package com.camelbytes.sitrep.unit.squadrons;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.squadrons.api.SquadronDto;
import com.camelbytes.sitrep.squadrons.internal.Squadron;
import com.camelbytes.sitrep.squadrons.internal.SquadronCreateRequest;
import com.camelbytes.sitrep.squadrons.internal.SquadronNotFoundException;
import com.camelbytes.sitrep.squadrons.internal.SquadronRepository;
import com.camelbytes.sitrep.squadrons.internal.SquadronService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class SquadronServiceTest {
  @Mock SquadronRepository repository;
  @InjectMocks SquadronService service;

  @Nested
  class GetSquadronById {
    @Test
    void getById_returnsDto() {
      Squadron squadron = new Squadron("1 Squadron", "1SQN");
      UUID id = UUID.randomUUID();
      ReflectionTestUtils.setField(squadron, "id", id);

      when(repository.findById(id)).thenReturn(Optional.of(squadron));

      SquadronDto expected = new SquadronDto(id, squadron.getName(), squadron.getShortName(), true);
      SquadronDto actual = service.getById(id);

      assertThat(actual).isEqualTo(expected);
    }
  }

  @Nested
  class CreateSquadron {
    @Test
    void createSquadron_duplicateName_throwsConflictException() {
      when(repository.save(any())).thenThrow(new DataIntegrityViolationException(""));
      SquadronCreateRequest request = new SquadronCreateRequest("1 Squadron", "1SQN");
      assertThatThrownBy(() -> service.createSquadron(request))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining(
              "Could not create squadron: Squadron name (1 Squadron) or short name (1SQN) already exists");
    }

    @Test
    void createSquadron_duplicateNameNullShortName_throwsConflictException() {
      when(repository.save(any())).thenThrow(new DataIntegrityViolationException(""));
      SquadronCreateRequest request = new SquadronCreateRequest("1 Squadron", null);
      assertThatThrownBy(() -> service.createSquadron(request))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining(
              "Could not create squadron: Squadron name (1 Squadron) or short name (null) already exists");
    }
  }

  @Nested
  class EnableSquadron {
    @Test
    void enableSquadron_enablesSquadron() {
      Squadron squadron = new Squadron("1 Squadron", "1SQN");
      UUID id = UUID.randomUUID();
      ReflectionTestUtils.setField(squadron, "id", id);
      ReflectionTestUtils.setField(squadron, "isActive", false);
      when(repository.findById(id)).thenReturn(Optional.of(squadron));

      service.enableSquadron(id);

      assertThat(squadron.isActive()).isTrue();
      verify(repository).save(squadron);
    }

    @Test
    void enableSquadron_alreadyEnabled_staysEnabled() {
      Squadron squadron = new Squadron("1 Squadron", "1SQN");
      UUID id = UUID.randomUUID();
      ReflectionTestUtils.setField(squadron, "id", id);
      when(repository.findById(id)).thenReturn(Optional.of(squadron));

      service.enableSquadron(id);

      assertThat(squadron.isActive()).isTrue();
      verify(repository).save(squadron);
    }

    @Test
    void enableSquadron_doesNotExist_throwsSquadronNotFoundException() {
      when(repository.findById(any())).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.enableSquadron(UUID.randomUUID()))
          .isInstanceOf(SquadronNotFoundException.class);
    }
  }

  @Nested
  class DisableSquadron {
    @Test
    void disableSquadron_disablesSquadron() {
      Squadron squadron = new Squadron("1 Squadron", "1SQN");
      UUID id = UUID.randomUUID();
      ReflectionTestUtils.setField(squadron, "id", id);
      when(repository.findById(id)).thenReturn(Optional.of(squadron));

      service.disableSquadron(id);

      assertThat(squadron.isActive()).isFalse();
      verify(repository).save(squadron);
    }

    @Test
    void disableSquadron_alreadyDisabled_staysDisabled() {
      Squadron squadron = new Squadron("1 Squadron", "1SQN");
      UUID id = UUID.randomUUID();
      ReflectionTestUtils.setField(squadron, "id", id);
      ReflectionTestUtils.setField(squadron, "isActive", false);
      when(repository.findById(id)).thenReturn(Optional.of(squadron));

      service.disableSquadron(id);

      assertThat(squadron.isActive()).isFalse();
      verify(repository).save(squadron);
    }

    @Test
    void disableSquadron_doesNotExist_throwsSquadronNotFoundException() {
      when(repository.findById(any())).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.disableSquadron(UUID.randomUUID()))
          .isInstanceOf(SquadronNotFoundException.class);
    }
  }
}
