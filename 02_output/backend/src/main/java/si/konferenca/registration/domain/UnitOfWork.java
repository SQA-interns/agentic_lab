package si.konferenca.registration.domain;

/** Port: runs work in one database transaction; an exception rolls it back. */
public interface UnitOfWork {

  void run(Runnable work);
}
