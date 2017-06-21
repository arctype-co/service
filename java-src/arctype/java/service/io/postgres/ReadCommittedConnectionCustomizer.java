package arctype.java.service.io.postgres;

import com.mchange.v2.c3p0.AbstractConnectionCustomizer;
import java.sql.Connection;
import java.sql.SQLException;

public class ReadCommittedConnectionCustomizer extends AbstractConnectionCustomizer
{
  
  @Override
  public void onAcquire(Connection conn, String token) throws Exception
  {
    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    super.onAcquire(conn, token);
  }

}
