package arctype.java.service.io.postgres;

import com.mchange.v2.c3p0.AbstractConnectionCustomizer;
import java.sql.Connection;
import java.sql.SQLException;

public class SerializableConnectionCustomizer extends AbstractConnectionCustomizer
{
  
  @Override
  public void onAcquire(Connection conn, String token) throws Exception
  {
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
    super.onAcquire(conn, token);
  }

}
