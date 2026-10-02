package si.konferenca.registration.adapter.out.persistence;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.UnitOfWork;

/** One database transaction per unit of work. */
public class TransactionalUnitOfWork implements UnitOfWork {

  private final TransactionTemplate transactionTemplate;

  public TransactionalUnitOfWork(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public void run(Runnable work) {
    transactionTemplate.executeWithoutResult(status -> work.run());
  }
}
