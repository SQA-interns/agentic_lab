package si.konferenca.registration.infrastructure.persistence;

import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.application.RegistrationPorts.Transactions;

/** One database transaction per call; a failed commit is thrown to the caller. */
public class SpringTransactions implements Transactions {

  private final TransactionTemplate template;

  public SpringTransactions(TransactionTemplate template) {
    this.template = template;
  }

  @Override
  public void inTransaction(Runnable work) {
    template.executeWithoutResult(status -> work.run());
  }
}
