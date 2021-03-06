/*analyze schema data to create setup database. This can be edited by the user before learning.
  If setup = 0, we skip this step and use the existing setup database
  Yan Sept 10th*/

import java.sql.DriverManager;
import java.sql.SQLException;
import com.mysql.jdbc.Connection;


public class MakeSetup {

	static Connection con1;

	//  to be read from config.cfg.
	// The config.cfg file should  be the working directory.
	static String databaseName, databaseName0;
	static String dbUsername;
	static String dbPassword;
	static String dbaddress;

	public static void main(String args[]) throws Exception {
		runMS();
	}
	
	public static void runMS() throws Exception {
		setVarsFromConfig();
		connectDB();
		//analyze schema data to create setup database. This can be edited by the user before learning.
		//If setup = 0, we skip this step and use the existing setup database
		
		BZScriptRunner bzsr = new BZScriptRunner(databaseName,con1);
		bzsr.runScript("src/scripts/setup.sql");  
		bzsr.createSP("src/scripts/storedprocs.sql");
        bzsr.callSP("find_values");
        
		disconnectDB();
	}
	
	
	public static void setVarsFromConfig(){
		Config conf = new Config();
		databaseName = conf.getProperty("dbname");
		databaseName0 = databaseName + "_setup";
		dbUsername = conf.getProperty("dbusername");
		dbPassword = conf.getProperty("dbpassword");
		dbaddress = conf.getProperty("dbaddress");
	}

	public static void connectDB() throws SQLException {
		//open database connections to the original database, the setup database, the bayes net database, and the contingency table database

		String CONN_STR1 = "jdbc:" + dbaddress + "/" + databaseName;
		try {
			java.lang.Class.forName("com.mysql.jdbc.Driver");
		} catch (Exception ex) {
			System.err.println("Unable to load MySQL JDBC driver");
		}
		con1 = (Connection) DriverManager.getConnection(CONN_STR1, dbUsername, dbPassword);
		
		
	}
	
	public static void disconnectDB() throws SQLException {
		con1.close();
	}
}
