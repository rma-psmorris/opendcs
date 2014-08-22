package opendcs.dbupdate;

import ilex.util.EnvExpander;
import ilex.util.Logger;

import java.io.Console;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import decodes.db.Database;
import decodes.sql.DecodesDatabaseVersion;
import decodes.tsdb.BadConnectException;
import decodes.tsdb.TsdbAppTemplate;
import decodes.tsdb.TsdbDatabaseVersion;
import decodes.util.CmdLineArgs;

public class DbUpdate extends TsdbAppTemplate
{
	private String username = null;
	private char []password = null;
	
	public DbUpdate(String logname)
	{
		super(logname);
	}

	@Override
	protected void runApp() throws Exception
	{
		System.out.println("Init done.");
		Database.getDb().networkListList.read();

		if (theDb.getTsdbVersion() < TsdbDatabaseVersion.VERSION_9)
		{
			System.out.println("This utility cannot be used on database versions before" +
				" version " + TsdbDatabaseVersion.VERSION_9 + ".");
			System.out.println("Your TSDB database is version " + theDb.getTsdbVersion()+ ".");
			System.out.println("You should create a new database using the scripts in the 'schema'"
				+ " directory that came with OpenDCS.");
			System.out.println("Then export records from the old database and import to the new.");
			System.exit(1);
		}

		if (theDb.getDecodesDatabaseVersion() == DecodesDatabaseVersion.DECODES_DB_10)
		{
			System.out.println("TSDB Database is currently " + theDb.getTsdbVersion());
			System.out.println("DECODES Database is currently " + theDb.getDecodesDatabaseVersion());
			sql("ALTER TABLE NETWORKLISTENTRY ADD COLUMN PLATFORM_NAME VARCHAR(24)");
			sql("ALTER TABLE NETWORKLISTENTRY ADD COLUMN DESCRIPTION VARCHAR(80)");

			String schemaDir = EnvExpander.expand("$DCSTOOL_HOME/schema/")
				+ (theDb.isCwms() ? "cwms30" : theDb.isOracle() ? "opendcs-oracle" : "opendcs-pg");

			if (!theDb.isCwms()) // CWMS doesn't support DCP Monitor Schema
			{
				SQLReader sqlReader = new SQLReader(schemaDir + "dcp_trans_expanded.sql");
				ArrayList<String> queries = sqlReader.createQueries();
				for(String q : queries)
					sql(q);
			}
			
			// The DACQ_EVENT table was not used before 11. Drop it and recreate below.
			sql("DROP TABLE DACQ_EVENT");
			
			// For Oracle, we'll need the table space originally defined on db creation.
			String tableSpaceSpec = null;
			SQLReader sqlReader = new SQLReader(schemaDir + "defines.sql");
			ArrayList<String> queries = sqlReader.createQueries();
			for(String q : queries)
				if (q.toUpperCase().startsWith("DEFINE TBL_SPACE_SPEC"))
				{
					tableSpaceSpec = q.substring(q.indexOf('\'') + 1);
					tableSpaceSpec = tableSpaceSpec.substring(0, q.indexOf('\''));
					break;
				}
			
			sqlReader = new SQLReader(schemaDir + "opendcs.sql");
			queries = sqlReader.createQueries();
			for(String q : queries)
				if (q.contains("CP_COMPOSITE_") // New tables CP_COMPOSITE_DIAGRAM and CP_COMPOSITE_MEMBER
				 || q.contains("DACQ_EVENT")    // Modified & previously unused DACQ_EVENT was dropped above
				 || q.contains("SERIAL_PORT_STATUS")) // New table for serial port status
				{
					// Oracle will have a table space definition that we need to substitute.
					int tblSpecIdx = q.indexOf("&TBL_SPACE_SPEC");
					if (tblSpecIdx != -1 && tableSpaceSpec != null)
						q = q.substring(0, tblSpecIdx) + tableSpaceSpec;
					sql(q);
				}
		
			if (!theDb.isOracle())
			{
				// NetworkListEntry now has columns PLATFORM_NAME and DESCRIPTION
				sql("ALTER TABLE NETWORKLISTENTRY ADD COLUMN PLATFORM_NAME VARCHAR(24)");
				sql("ALTER TABLE NETWORKLISTENTRY ADD COLUMN DESCRIPTION VARCHAR(80)");
				
				// TransportMedium has several new columns to support data loggers via modem & network.
				sql("ALTER TABLE TRANSPORTMEDIUM ADD COLUMN LOGGERTYPE VARCHAR(24)");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD COLUMN BAUD INT");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD COLUMN STOPBITS INT");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD COLUMN PARITY VARCHAR(1)");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD COLUMN DATABITS INT");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD COLUMN DOLOGIN VARCHAR(5)");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD COLUMN USERNAME VARCHAR(32)");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD COLUMN PASSWORD VARCHAR(32)");
			}
			else
			{
				sql("ALTER TABLE NETWORKLISTENTRY ADD PLATFORM_NAME VARCHAR2(24)");
				sql("ALTER TABLE NETWORKLISTENTRY ADD DESCRIPTION VARCHAR2(80)");
				
				sql("ALTER TABLE TRANSPORTMEDIUM ADD LOGGERTYPE VARCHAR2(24)");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD BAUD INT");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD STOPBITS INT");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD PARITY VARCHAR2(1)");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD DATABITS INT");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD DOLOGIN VARCHAR2(5)");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD USERNAME VARCHAR2(32)");
				sql("ALTER TABLE TRANSPORTMEDIUM ADD PASSWORD VARCHAR2(32)");
			}


			// Update DECODES Database Version
			sql("UPDATE DECODESDATABASEVERSION SET VERSION_NUM = " + DecodesDatabaseVersion.DECODES_DB_11);
			theDb.setDecodesDatabaseVersion(DecodesDatabaseVersion.DECODES_DB_11, "");
			// Update TSDB_DATABASE_VERSION.
			String desc = "Updated on " + new Date();
			sql("UPDATE TSDB_DATABASE_VERSION SET DB_VERSION = " + TsdbDatabaseVersion.VERSION_10
				+ ", DESCRIPTION = '" + desc + "'");
			theDb.setTsdbVersion(TsdbDatabaseVersion.VERSION_10, desc);
			Database.getDb().networkListList.write();

		}
	}

	private void sql(String query)
	{
		System.out.println("Executing: " + query);
		try
		{
			theDb.doModify(query);
		}
		catch (Exception ex)
		{
			System.out.println("ERROR: " + ex);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args)
		throws Exception
	{
		DbUpdate app = new DbUpdate("dbupdate.log");
		app.execute(args);
	}

	@Override
	protected void addCustomArgs(CmdLineArgs cmdLineArgs)
	{
		appNameArg.setDefaultValue("utility");
		// Use console to ask for user name.
		Console console = System.console();
		console.writer().println("Enter user name and password for the CP/DECODES schema owner account.");
		console.writer().print("CP schema owner user name: ");
		console.writer().flush();
		username = console.readLine();
		console.writer().print("Password: ");
		console.writer().flush();
		password = console.readPassword();
	}

	/**
	 * Ask user for username & password for database connection.
	 * Then connect.
	 * Use console.
	 */
	@Override
	public void tryConnect()
		throws BadConnectException
	{
		// Connect to the database!
		Properties props = new Properties();
		props.setProperty("username", username);
		props.setProperty("password", new String(password));

		String nm = appNameArg.getValue();
		Logger.instance().info("Connecting to TSDB as user '" + username + "'");
		appId = theDb.connect(nm, props);
	}
}