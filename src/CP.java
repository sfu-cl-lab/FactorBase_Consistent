/*
*	Sajjad: The only changes I made are: (Sep 30, 2016)
*		- Adding updateParentSum method which is actually do the sort-merge join process
*		- Change method haspar_update a bit to be consistent with the updateParentSum and call it
*	So the input and output are the same as before. It makes the process of computation faster using sor-merge ;)
*/

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/* Feb 7, 2014 @ zqian, bug: miss some nodes which ONLY act as Parent
 * fixed: for each lattice point,  add one extra step after the structure learning is done
 *Feb 6 Yan 
 * Change ChildValue to FID
 * Fix bug: CP sum up to 1 in IMDB 
 
*/

/* Try to using the formula in Oliver's SDM11 paper
 * AIC =  mult * loglikelihood  - #parameters 
 * BIC = 2 * local_mult * loglikelihood   - parameters * log(BigSampleSize)
 * 
 * 
 * Zqian@Dec 10th, 2013
 * 
 * */

/*
 * add only the small mults where log(CP) < 0, i.e. CP < 1
 * this would help a little bit,
 * However, we need to think about the following situtation:
 * suppose for all of the configuration of one node, the CP = 1.0, then how to decide the sample size???
 * 
 * So basically, we aborted this idea. Zqian@Dec 10th, 2013
 * *
 */
 

/*editing the float type [float(20,2)],  Zqian@Dec 6th, 2013
 * 
 * */

/* zqian @ Dec 5th 2013
 * computing BIC and AIC based on local counts
 * 
 * ___________________________________ computation based on *Biggest CT*
 * AIC =  mult * loglikelihood  - #parameters 
 * BIC = 2 * mult * loglikelihood - #parameters * log (SampleSize)
 * 
 * normalized version 
 * AICNormal = (mult * loglikelihood) / [sum(mult) in bigCT table] - #parameters / [sum(mult) in bigCT table] 
 * BICNormal = (2 * mult * loglikelihood ) / [sum(mult) in bigCT table] 
 *                                  -   [#parameters * log (SampleSize)] / [sum(mult) in bigCT table]   
 * 
 * however this is not quite correct, so we need to compute it *locally*
 * __________________________________________________________
 * local_AIC =  local_mult * loglikelihood  - #parameters 
 * local_BIC = 2 * local_mult * loglikelihood - #parameters * log (local_SampleSize)
 *  
 * normalized version
 * local_AICNormal = (local_mult * loglikelihood) / [sum(mult) in local CT table] - #parameters / [sum(mult) in local CT table]
 * local_BICNormal = (2 * local_mult * loglikelihood ) / [sum(mult) in local CT table] 
 *                                  -   [#parameters * log (local_SampleSize)] / / [sum(mult) in local CT table]    
 * 
 */

/*Written by Qing & Sara*/
 
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import com.mysql.jdbc.ResultSetMetaData;
 
 
/* finds conditional probabilities for the Bayes net parameters. Also computes log-likelihood and other scores for each node. */
 
public class CP {
 static Connection con1;
 // to be read from config
 static String databaseName;
 static String real_database;
 static String databaseName2;
 static String dbUsername="";
 static String dbPassword="";
 static String dbaddress="";
 static String rchain ;
 
 
public CP(String databaseName, String databaseName2){
 
        CP.databaseName = databaseName;
 
        CP.databaseName2 = databaseName2;
 
 
}

 public static void cp() throws Exception {
	 long l = System.currentTimeMillis(); //@zqian : measure parameter learning time
	 setVarsFromConfig();
 	 connectDB();
 	 CP(rchain,con1);
 
 	long l2 = System.currentTimeMillis();  //@zqian : measure parameter learning time
	System.out.print("Parameter Learning Time(ms): "+(l2-l)+" ms.\n");

 
 	 
 }
 
 public static void CP(String rchain,Connection con1) throws SQLException {
 	 //Builds CP table for a given rchain. Right now, it's the longest one for the final result
 	 
 	 prepare(rchain, con1);
 	 nopar(rchain, con1);
 	 haspar(rchain,con1);
  	//Feb 7, 2014 @ zqian, bug: miss some nodes which ONLY act as Parent
    // fixed by add such node as a child without parent in Path_BayesNet

 	 Statement st2 = con1.createStatement();	 

 	st2.execute("update Scores set Normal_LogLikelihood= LogLikelihood/SampleSize ;");
 	st2.execute("update Scores set AIC = LogLikelihood - Parameters ;");
 	st2.execute("update Scores set BIC = 2*LogLikelihood - Log(SampleSize) * Parameters ;");
 	
 	st2.execute("update Scores set Pseudo_AIC = Normal_LogLikelihood - Parameters ;");
 	st2.execute("update Scores set Pseudo_BIC = 2*Normal_LogLikelihood - Log(SampleSize) * Parameters ;");
 	//st2.execute("update Scores set Pseudo_BIC = 2*Normal_LogLikelihood - Log(Big_SampleSize) * Parameters ;"); ??
 	//st2.execute("update Scores set AICNormal = AIC/SampleSize ;");
 	//st2.execute("update Scores set BICNormal = BIC/SampleSize ;");
 	st2.close();
 }
 
 
 
 public static void prepare(String rchain,Connection con1) throws SQLException {
 	 //prepares a table to record scores for each node. These are computed later. For now we just compute the number of parameters for each node given the BN structure.
 	 //The number of parameters is basically (number of parent states) * (number of child values - 1). 
	 //The main complication is that if a relationship is false, then its descriptive attributes must be N/A.
 	 //So the number of effective parameters is less than just the standard number of CP-table rows.
	 
	 lcoal_mult_update(rchain,con1); //zqian @ Dec 4th
 	 
	 Statement st = con1.createStatement();
 	 st.execute("drop table if exists Scores;");
 	 st.execute("create table Scores (`Fid` varchar(255) NOT NULL, " + //zqian Oct 17
 	 	 	 	 "`LogLikelihood` float(20,2) default NULL, " + //editing the float type
 	 	 	  "`Normal_LogLikelihood` float(20,2) default NULL, " + //editing the float type
 	 	 	 	 "`Parameters` bigint(20) default NULL, " +
 	 	 	 	 "`SampleSize` bigint(20) default NULL, " +
 	 	 	 	 "`BIC` float(20,2) default NULL, " +
 	 	 	 	 "`AIC` float(20,2) default NULL, " +
 	 	 	 	 "`Pseudo_BIC` float(20,2) default NULL, " +
	 	 	 	 "`Pseudo_AIC` float(20,2) default NULL, " +
	 	 	 	 "`Big_SampleSize` bigint(20) default NULL, " +
// 	 	 	 	 "`BICNormal` float(20,2) default NULL, " +
// 	 	 	 	 "`AICNormal` float(20,2) default NULL, " +
 	 	 	 	 " PRIMARY KEY (`Fid`))");
 	 // Feb 7, 2014 @ zqian, bug: miss some nodes which ONLY act as Parent  
 	 st.execute("insert into Scores(Fid) SELECT distinct child from Path_BayesNets where Rchain = '" + rchain +"';");
 	 
 	 st.execute("update FNodes,RNodes set FunctorName = (select distinct rnid from RNodes where FNodes.FunctorName "+
 	 	 	 "= RNodes.TABLE_NAME and FNodes.Fid = RNodes.rnid) where FNodes.FunctorName "+
 	 	 	 "= RNodes.TABLE_NAME and FNodes.Fid = RNodes.rnid;");
 	 
 	 
 	 st.execute("drop table if exists NumAttributes;");
 	 st.execute("create table NumAttributes as SELECT count(VALUE) as NumAtts, COLUMN_NAME FROM Attribute_Value group by COLUMN_NAME;");
 	 
 	 st.execute("drop table if exists RNodes_inFamily;");
 	 st.execute("create table RNodes_inFamily as select FamilyRNodes.child as ChildNode, FamilyRNodes.parent as Rnode " +
 	 	 	 	 " FROM Path_BayesNets as FamilyRNodes, FNodes as RNode_check " +
 	 	 	 	 " where FamilyRNodes.Rchain = '" + rchain + "' and RNode_check.Fid = FamilyRNodes.parent and RNode_check.Type = 'RNode';");
 	 
 	 st.execute("drop table if exists 2Nodes_inFamily;");
 	 st.execute("create table 2Nodes_inFamily as select Family2Nodes.child as ChildNode, Family2Nodes.parent as 2node, NumAttributes.NumAtts " +
 	 	 	 	 " FROM Path_BayesNets as Family2Nodes, FNodes as 2Node_check, NumAttributes where Family2Nodes.Rchain = '" + rchain + "' and 2Node_check.Fid = Family2Nodes.parent " +
 	 	 	 	 " and 2Node_check.Type = '2Node' and 2Node_check.FunctorName = NumAttributes.COLUMN_NAME;");
 	 
 	 st.execute("drop table if exists 1Nodes_inFamily;");
 	 st.execute("create table 1Nodes_inFamily as	 select Family1Nodes.child as ChildNode, Family1Nodes.parent as 1node, NumAttributes.NumAtts " +
 	 	 	 	 " FROM Path_BayesNets as Family1Nodes, FNodes as 1Node_check, NumAttributes where Family1Nodes.Rchain = '" + rchain + "' and 1Node_check.Fid = Family1Nodes.parent" +
 	 	 	 	 " and 1Node_check.Type = '1Node' and 1Node_check.FunctorName = NumAttributes.COLUMN_NAME ;");
 	 
 	 st.execute("drop table if exists RNodes_2Nodes_Family;");
 	 st.execute("create table RNodes_2Nodes_Family as select RNodes_inFamily.ChildNode, RNodes_inFamily.Rnode, 2Nodes_inFamily.2Node, 2Nodes_inFamily.NumAtts " +
 	 	 	 	 " from RNodes_inFamily, 2Nodes_inFamily where RNodes_inFamily.ChildNode = 2Nodes_inFamily.ChildNode and " +
 	 	 	 	 " (RNodes_inFamily.Rnode, 2Nodes_inFamily.2Node) in (select * from RNodes_2Nodes);");
 	 
 	 st.execute("drop table if exists ChildPars;");
 	 st.execute("create table ChildPars as SELECT distinct (NumAtts-1) as NumPars, FNodes.Fid as ChildNode FROM " +
 	 	 	 	 " FNodes join NumAttributes where FNodes.FunctorName=NumAttributes.COLUMN_NAME;");
 	 
 	 st.execute("drop table if exists 1NodePars;");
 	 st.execute("create table 1NodePars as select ChildNode, exp(sum(log(NumAtts))) as NumPars from 1Nodes_inFamily group by ChildNode union " +
 	 	 	 	 " select distinct child as ChildNode, 1 as NumPars from Path_BayesNets where Path_BayesNets.Rchain = '" + rchain + "' and child not in (select ChildNode from 1Nodes_inFamily);");
 	 
 	 st.execute("drop table if exists RelationsParents;");
 	 st.execute("create table RelationsParents as select ChildNode, rnid, 2node, NumAtts from 2Nodes_inFamily, RNodes_2Nodes where 2node = 2nid union " +
 	 	 	 	 " select ChildNode, RNode as rnid, RNode, 1 as NumVals from RNodes_inFamily;");
 	 
 	 st.execute("drop table if exists RelationsPars;");
 	 st.execute("create table RelationsPars as select ChildNode, exp(sum(log(NumPars))) as NumPars from (select ChildNode, rnid, " +
 	 	 	 	 " exp(sum(log(NumAtts)))+1 as NumPars from RelationsParents group by ChildNode, rnid) as ParPerRelation group by ChildNode;");
 	//why we're doing this again?line 267, Nov 12 Zqian
 	 /*	 st.execute("update FNodes,RNodes set FunctorName = (select distinct rnid from RNodes where FNodes.FunctorName "+
 	 	 	 "= RNodes.TABLE_NAME and FNodes.Fid = RNodes.rnid) where FNodes.FunctorName "+
 	 	 	 "= RNodes.TABLE_NAME and FNodes.Fid = RNodes.rnid;");
 	*/
 	 st.execute("update Scores, ChildPars, 1NodePars, RelationsPars set Parameters= " +
 	 	 	 	 " (select ChildPars.NumPars * 1NodePars.NumPars * RelationsPars.NumPars from ChildPars, 1NodePars, RelationsPars " +
 	 	 	 	 " where ChildPars.ChildNode = 1NodePars.ChildNode and 1NodePars.ChildNode = RelationsPars.ChildNode and Scores.Fid = RelationsPars.ChildNode ) " +
 	 	 	 	 " where RelationsPars.ChildNode= Scores.Fid;");
 	 
 	 st.execute("update Scores, ChildPars, 1NodePars set Parameters= " +
 	 	 	 	 " (select ChildPars.NumPars * 1NodePars.NumPars from ChildPars, 1NodePars" +
 	 	 	 	 " where ChildPars.ChildNode = 1NodePars.ChildNode and 1NodePars.ChildNode = Scores.Fid ) where 1NodePars.ChildNode = Scores.Fid and Parameters is NULL;");
 	 
 	 //why we're doing this again? line 272, Nov 12 Zqian
 	 /* st.execute("drop table if exists NumAttributes;");
 	 st.execute("CREATE TABLE NumAttributes as SELECT COLUMN_NAME, count(VALUE) as NumAtts FROM Attribute_Value group by COLUMN_NAME;");
 	 */
 	 st.close();
 }
  
 //deal with nodes without parents
 public static void nopar(String rchain,Connection con1) throws SQLException {
 	java.sql.Statement st = con1.createStatement();
 	ResultSet rst=st.executeQuery("SELECT child FROM Path_BayesNets WHERE Rchain='" + rchain + 
 			 "' AND parent = '' and child not in (SELECT distinct child FROM Path_BayesNets WHERE parent<>'' and Rchain= '" + rchain + "');");
 	System.out.println("SELECT child FROM Path_BayesNets WHERE Rchain='" + rchain + 
			 "' AND parent = '' and child not in (SELECT distinct child FROM Path_BayesNets WHERE parent<>'' and Rchain= '" + rchain + "');");
 	ArrayList<String> noparent_tables = new ArrayList<String>();
 	 
 	String bigTable = rchain.substring(0, rchain.length()-1) + "_CT`";
 	 System.out.println("bigTable Name: " + bigTable +"\n"); 	 
 	while(rst.next()){
 	 	 System.out.println("noparent node: " + rst.getString(1));
 	 	 noparent_tables.add(rst.getString(1));
 	 }
 	 //zqian Nov 13, computing sum(mult) from biggest CT table
 	 //and close the connections
 	java.sql.Statement st2 = con1.createStatement();	 
 	String sql2 = "select sum(MULT) from "+databaseName2 + "." + bigTable + ";"; // only need to do this query once, Nov 12 zqian
 	System.out.println(sql2 + "\n");
 	ResultSet deno = st2.executeQuery(sql2);
 	deno.absolute(1);
 	long mydeno = Long.parseLong(deno.getString(1));  	 //convert string to integer
 	System.out.println("SUM(mult) in bigCTTable : "+mydeno + "\n");
 	for(int i=0; i<noparent_tables.size(); i++) {
 	 	 nopar_update(rchain,bigTable,noparent_tables.get(i),con1,mydeno);
 	 }
 	
 	
 	
 	 st2.close();
 	 st.close();
 }
 //similar simpler computation for nodes without parents.
 public static void nopar_update(String rchain,String bigTable,String nodeName, Connection con1, long mydeno) throws SQLException {
	 java.sql.Statement st = con1.createStatement();	 
	 java.sql.Statement st2 = con1.createStatement();	 
	 String table_name = nodeName.substring(0, nodeName.length()-1) + "_CP`";
	 st.execute("drop table if exists " + table_name+ ";");
	 System.out.println(table_name+"\n");
	 //change the ChildValue to FID -- Jan 23 Yan
	 st.execute("create table " + table_name + " ( " + nodeName + " varchar(200) NOT NULL, CP float(7,6), MULT bigint(20), local_mult bigint(20))");
	 st.execute("insert into " + table_name + "(" + nodeName + ") select distinct " + nodeName + " from " +databaseName2 + "." + bigTable + ";");
	 
	 ResultSet rst=st.executeQuery("select " + nodeName + " from " + table_name + ";");
	 ArrayList<String> column_value = new ArrayList<String>();
	 while(rst.next()){
	 	 System.out.println("column value: " + rst.getString(1) + "\n");
	 	 column_value.add(rst.getString(1));
	 }
	 
	 for(int i=0; i<column_value.size(); i++) {
	 	 String sql = "select sum(MULT) from " +databaseName2 + "." + bigTable + " where " + nodeName + " = '" + column_value.get(i) + "';";
	 	 System.out.println(sql+"\n");
	 	 ResultSet nume = st2.executeQuery(sql);
	 	 //nume is the sum over all contingency table rows for a specific value.
	 	 nume.absolute(1);
	 	 long mynume = Long.parseLong(nume.getString(1));
	 	 //converts string to integer
	 	 System.out.println(mynume + "\n");
 
	 	//change the ChildValue to FID -- Jan 23 Yan
	 	String sql3 = "update " + table_name + " set MULT = " + mynume + " where " + nodeName + " = '" + column_value.get(i) + "';";
	 	System.out.println(sql3 + "\n");
	 	st2.execute(sql3);
	 	st2.execute("update " + table_name + " set CP = MULT / " + mydeno + " where "  + nodeName + " = '" + column_value.get(i) +"';");
	 	
	 	ResultSet rs = st.executeQuery("select Tuples from  Pvars_Not_In_Family where child = '"+ nodeName +"' ;");
	 	long local = 1;
		while(rs.next()){
				local = Long.parseLong (rs.getString("Tuples"));
				System.out.println("local is "+ local);
				mynume = mynume / local;
				//System.out.println("set local_mult = mult, May 21, 2014 zqian ");
				 // set local_mult = mult, May 21, 2014 zqian

		}
		if (!rs.first()) {
 	 		System.out.println("local is 1, ******" );
 	 		mynume = mynume / local;
 	 	}
		 //updating the local_mult = mult / local ,  Dec 3rd
		String sql4 = "update " + table_name + " set local_mult = " + mynume + " where " + nodeName + " = '" + column_value.get(i) + "';";
	 	System.out.println(sql4 + "\n");
	 	st2.execute(sql4);	 	 
	 	 
	 }
	 
	 st2.execute("Alter table " + table_name + " add `likelihood` float(20,2);" );
	 st2.execute("update " + table_name + " set `likelihood` =log(CP)*local_mult;");//Dec 2nd, likelihood = log(cp) * mult
	 //LOG10() Return the base-10 logarithm of the argument LOG2() Return the base-2 logarithm of the argument //
	 //LOG() Return the natural logarithm of the first argument

	 
	 ResultSet logsum = st2.executeQuery("select sum(likelihood) as loglike from " + table_name + ";");
	 logsum.absolute(1);
	 double mylogsum = logsum.getDouble(1);
	 st2.execute("update Scores set LogLikelihood= " + mylogsum + " where Scores.Fid = '" + nodeName + "';");
	 ResultSet samplesize = st2.executeQuery("select sum(local_mult) from " + table_name + ";");
	 //ResultSet samplesize = st2.executeQuery("select sum(local_mult) from " + table_name + " where cp < 1.0 ;");

	 samplesize.absolute(1);
	 long mysize = Long.parseLong(samplesize.getString(1));
	 st2.execute("update Scores set SampleSize = " + mysize + " where Scores.Fid = '" + nodeName + "';");
	 ResultSet big_samplesize = st2.executeQuery("select sum(mult) from " + table_name + ";");

	 big_samplesize.absolute(1);
	 long big_mysize = Long.parseLong(big_samplesize.getString(1));
	 st2.execute("update Scores set Big_SampleSize = " + big_mysize + " where Scores.Fid = '" + nodeName + "';");
 	 
	 //compute the prior June 23, 2014, zqian
	 st.execute("alter table " + table_name + " add prior float(7,6);");
	 st.execute("UPDATE " + table_name + " SET prior = CP ;");
	 
	st2.close();
	 st.close(); 
}

 //deal with child nodes that have parents.
 public static void haspar(String rchain,Connection con1) throws SQLException {
 	 java.sql.Statement st = con1.createStatement();
 	 st.execute("drop table if exists childrenT;");
 	 ResultSet rst=st.executeQuery("select distinct child FROM Path_BayesNets where Rchain='" + rchain + "' and parent <> ''");
 	 ArrayList<String> hasparent_tables = new ArrayList<String>();
 	 
 	 String bigTable = rchain.substring(0, rchain.length()-1) + "_CT`";
 	 //find name of contingency table that has the data we need for the chain
 	 	 	 
 	 while(rst.next()){
 	 	 System.out.println("hasparent node: " + rst.getString(1));
 	 	 hasparent_tables.add(rst.getString(1));
 	 }
 	 for(int i=0; i<hasparent_tables.size(); i++) {
 	 	 haspar_update(rchain,bigTable,hasparent_tables.get(i),con1,databaseName2);
 	 }
 	 st.close();
 }
 //actually compute conditional probabilities
 public static void haspar_update(String rchain,String bigTable,String nodeName, Connection con1,String CT_Scheme) throws SQLException {
	 
	 //Saj
	 File ff = new File( "imdb_MovieLens/CG_queries.txt");
	 FileWriter fww;
	 BufferedWriter bww;
	 
	
	 
	
	 java.sql.Statement st = con1.createStatement();	 
 	 ResultSet parents = st.executeQuery("select distinct parent from Path_BayesNets where Rchain= '" + rchain + "' and child= '" + nodeName + "' and parent != '';");
 	 ArrayList<String> parent_name = new ArrayList<String>();
 	 while(parents.next()){
 	 	 System.out.println("parent value: " + parents.getString(1) + "\n");
 	 	 parent_name.add(parents.getString(1));
 	 }
 
 	 String from_st = parent_name.get(0);
 	 for(int i=1; i<parent_name.size();i++)
 	 {
 	 	 from_st = from_st + " , " + parent_name.get(i);
 	 }
 	 System.out.println("from clause: " +from_st+"\n");
 	 
 	 //general strategy: apply group by parent values to CT table, to find sum of parent counts. Then use that to divide the joint child-family count, which we get from the CT table.
 	 String table_name = nodeName.substring(0, nodeName.length()-1) + "_CP`";
 	 System.out.println(table_name + "\n");
 	 
 	 
 	 
// 	 if(!table_name.contains("age(users0)_CP"))
// 		 System.exit(1);
 	 
 	 
 	 
 	 st.execute("drop table if exists " + table_name + ";");
 	 st.execute("drop table if exists temp;");
 	 st.execute("drop table if exists temp2;");
	
 	//change the ChildValue to FID -- Jan 23 Yan
// 	 System.out.println("create table " + table_name + " as select sum(MULT) as MULT, " + nodeName + " , " + from_st 
// 			 + " , 0 as ParentSum from " +databaseName2 + "." + bigTable +" group by " + nodeName + ", " + from_st +";" );
 	
// 	 st.execute("create table " + table_name + " as select sum(MULT) as MULT, " + nodeName + "  , " + from_st 
// 			 + " , 0 as ParentSum from " +databaseName2 + "." + bigTable +" group by " + nodeName + ", " + from_st +";" );
// 	 st.execute("Alter table " + table_name + " CHANGE COLUMN ParentSum ParentSum bigint(20);");
//	 st.execute("Alter table " + table_name + " add `local_mult` bigint(20);" );
	 
	 st.execute("create table temp as select sum(MULT) as MULT, " + nodeName + "  , " + from_st 
 			 + " ,0 as `ParentSum` from " +databaseName2 + "." + bigTable +" group by " + nodeName + ", " + from_st +";" );

 	 st.execute("ALTER TABLE temp MODIFY `ParentSum` decimal(42,0)");
 	 //add index to CP table
	//change the ChildValue to FID -- Jan 23 Yan
// 	 String index = "ALTER TABLE " +  table_name + " ADD INDEX " + table_name + "( " + nodeName + " ASC ";
//// 	 for (int i=0; i<parent_name.size(); ++i) {
// 	 for (int i=0; i<Math.min(17,parent_name.size()); ++i) {
// 		 index = index + ", " + parent_name.get(i) + " ASC ";
// 	 }
// 	 index = index + ");";
// 	//System.out.println(index);
// 	// //Dec 12 
// 	 st.execute(index);
 	 
 	 
 	String index = "ALTER TABLE temp ADD INDEX temp_ ( " + nodeName + " ASC ";
//	 for (int i=0; i<parent_name.size(); ++i) {
	 for (int i=0; i<Math.min(15,parent_name.size()); ++i) {
		 index = index + ", " + parent_name.get(i) + " ASC ";
	 }
	 index = index + ");";
	System.out.println(index);
	// //Dec 12 
	 st.execute(index);
 	
 		
//	 System.out.println("create table temp2 as select MULT, " + from_st + ", sum(MULT) as ParentSum from " + table_name + " group by " + from_st + ";" );
// 	 st.execute("create table temp as select MULT, " + from_st + ", sum(MULT) as ParentSum from " + table_name + " group by " + from_st + ";" );
 	 
 	System.out.println("create table temp2 as select MULT, " + from_st + ", sum(MULT) as ParentSum from " + table_name + " group by " + from_st + ";" );
	 st.execute("create table temp2 as select MULT, " + from_st + ", sum(MULT) as ParentSum from temp group by " + from_st + ";" );

	 st.execute("ALTER TABLE temp2 MODIFY `ParentSum` decimal(42,0)");
 	 //add index to temp table
 	 String index_temp = "ALTER TABLE temp2 ADD INDEX  temp2_ ( "+ parent_name.get(0) +" ASC";
// 	 for (int i=1; i<parent_name.size(); ++i) {
 	 for (int i=1; i<Math.min(15,parent_name.size()); ++i) {
 		 index_temp = index_temp  + ", "  + parent_name.get(i) + " ASC  ";
 	 }
 	 index_temp = index_temp + ");";
 	 System.out.println(index_temp);
 	//Dec 12 
 	 st.execute(index_temp);
 	 
// 	 String updateclause = "update " + table_name + ", temp set " + table_name + ".ParentSum=temp.ParentSum where " 
// 			 + table_name + "." + parent_name.get(0) + "=temp." + parent_name.get(0);
// 	 for (int i=1; i<parent_name.size(); ++i) {
// 		updateclause = updateclause + " and " + table_name + "." + parent_name.get(i) + "=temp." + parent_name.get(i);
// 	 }
// 	 System.out.println(updateclause + ";");
// 	 st.execute(updateclause + ";");
 	 
 	try {
		updateParentSum("temp", "temp2", table_name, nodeName, from_st, con1);
	} catch (IOException e) {
		e.printStackTrace();
	}
 	 
 	 
 	 
 	 st.execute("alter table " + table_name + " add CP float(20,6);");
 	 
// 	 our resolution is only up to 6 digits. This is mainly to help with exporting to BIF format later.
 	 st.execute("alter table " + table_name + " add likelihood float(30);");
 	  	 
 	 st.execute("update " + table_name + " set CP = MULT / ParentSum ;");
 	 
 	 st.execute("update " + table_name + " set likelihood = log(CP)*mult;");//Nov 29, likelihood = log(cp) * mult
 	try { 
		fww = new FileWriter(ff, false);
		bww = new BufferedWriter(fww);
		bww.write("create table " + table_name + " as select sum(MULT) as MULT, " + nodeName + "  , " + from_st 
	 			 + " , 0 as ParentSum from " +databaseName2 + "." + bigTable +" group by " + nodeName + ", " + from_st +";");
		bww.write("\n\n");
		bww.write("Alter table " + table_name + " CHANGE COLUMN ParentSum ParentSum decimal(42,0);");
		bww.write("\n\n");
		bww.write("Alter table " + table_name + " add `local_mult` decimal(42,0);");
		bww.write("create table temp as select MULT, " + from_st + ", sum(MULT) as ParentSum from " + table_name + " group by " + from_st + ";");
		bww.write("\n\n");
//		bww.write(updateclause + ";");
		bww.write("\n\n");
		bww.write("alter table " + table_name + " add CP float(9,6);");
		bww.write("\n\n");
		bww.write("alter table " + table_name + " add likelihood float(30,2);");
		bww.write("\n\n");
		bww.write("update " + table_name + " set CP = MULT / ParentSum ;");
		bww.write("\n\n");
		bww.write("\n\n");
		bww.close();
	}catch(Exception ex){
		ex.printStackTrace();
	}
  	
 	ResultSet rs = st.executeQuery("select Tuples from  Pvars_Not_In_Family where child = '"+ nodeName +"' ;");
 	java.sql.Statement st1 = con1.createStatement();
	long local = 1;
		
 		while(rs.next()){
			local = Long.parseLong (rs.getString("Tuples"));
			System.out.println("local is "+ local);
			String sql = "update "+ table_name + " set local_mult = mult/ "+local + " ;";
			//String sql = "update "+ table_name + " set local_mult = mult  ;";
			System.out.println(sql);
			//set local_mult = mult, May 21, 2014 zqian
			st1.execute(sql);			
		}
 		if (!rs.first()) {
 	 		System.out.println("local is 1, ******" );
 			String sql = "update "+ table_name + " set local_mult = mult/ "+local + " ;";
 			st1.execute(sql);	
 	 	}
 	
 	st.execute("update " + table_name + " set likelihood = log(CP)*local_mult;");
 	
// 	st.execute("drop table if exists temp1;");
// 	st.execute("create table temp1 as select * from temp;");
 	 st.execute("drop table if exists temp;");
 	 st.execute("drop table if exists temp2;");
 	 
 	 //next, compute scores for each node.
 	 
 	 ResultSet mylog = st.executeQuery("select sum(likelihood) from " + table_name +";");
 	 mylog.absolute(1);
 	 double mylogsum = mylog.getDouble(1);
 	 st.execute("update Scores set LogLikelihood= " + mylogsum + " where Scores.Fid = '" + nodeName +"';");

 	 ResultSet mysample = st.executeQuery("select sum(local_mult) from " + table_name + ";");
 	

 	// ResultSet mysample = st.executeQuery("select sum(local_mult) from " + table_name + " where cp < 1.0 ;");
	 long size=0;
	 mysample.absolute(1);
 	 size = Long.parseLong(mysample.getString(1));
 	 ResultSet big_mysample = st.executeQuery("select sum(mult) from " + table_name + ";");
 	long big_size=0;
	 big_mysample.absolute(1);
	 big_size = Long.parseLong(big_mysample.getString(1));
  	 st.execute("update Scores set Big_SampleSize = " + big_size + " where Scores.Fid = '" + nodeName + "';");
 
 	 st.execute("update Scores set SampleSize = " + size + " where Scores.Fid = '" + nodeName + "';");
 	 st.execute("update Scores set AIC = LogLikelihood - Parameters where Scores.Fid = '" + nodeName + "';");
 	 st.execute("update Scores set BIC = 2*LogLikelihood - Log(SampleSize) * Parameters where Scores.Fid = '" + nodeName + "';");
// 	 st.execute("update Scores set AICNormal = AIC/SampleSize where Scores.Fid = '" + nodeName + "';");
// 	 st.execute("update Scores set BICNormal = BIC/SampleSize where Scores.Fid = '" + nodeName + "';");
 	 
 	 //compute the prior June 23, 2014, zqian
 	 st.execute("alter table " + table_name + " add prior float(7,6);");
	 ResultSet rst1 = st.executeQuery("Select sum(local_mult) from " + table_name);
	 rst1.absolute(1);
	 long total_sum = rst1.getLong(1);
	 
	 st.execute("DROP TABLE IF EXISTS temp;");
	 String createtemp = "CREATE TABLE IF NOT EXISTS temp SELECT sum(local_mult) as prior_parsum, `"+ nodeName.replace("`","") + "` FROM " + table_name + " GROUP BY `" +  nodeName.replace("`","")   +"` ;" ;
	 System.out.println("temp: " + createtemp);
	 st.execute(createtemp);
	 
	 String updateprior = "UPDATE " + table_name + ", temp" + " SET " + table_name + ".prior= temp.prior_parsum/"+ total_sum
			 	+  " WHERE " + table_name + ".`" + nodeName.replace("`","") + "`=temp.`" + nodeName.replace("`","") + "` ; ";
	 
	 System.out.println("updateprior: " + updateprior);
	 st.execute(updateprior); 
	 st.execute("DROP TABLE IF EXISTS temp;"); 	 
 	 
 	 
 	 
 	 
 	 st.close();
 }
 
 
 //prepare for the computing of local_mult, Dec 3rd, zqian
 public static void lcoal_mult_update(String rchain, Connection con1) throws SQLException{
	 	 
	 	 java.sql.Statement st = con1.createStatement();
	 	 //for each node find its associated population variables 
	 	 //(e.g:RA(prof0,student0) as Fid, then it should have two pvid : prof0 and student0)
	 	 st.execute("drop table if exists FNodes_pvars_UNION_RNodes_pvars;");
	 	 st.execute("create table FNodes_pvars_UNION_RNodes_pvars as "
	 	 		+ " SELECT rnid as Fid , pvid FROM RNodes_pvars union distinct SELECT * FROM FNodes_pvars;");
	 	 //for each configuration(i.e. each node with all its parents ), find its associated population variables
	 	 //(configuration 1:
	 	 //		intel(student0) <--- RA(prof0,student0), 
	 	 //		then return prof0, and student0 )
	 	 //( or configuration 2:
	 	 //     intel(student0) <--- RA(prof0,student0)
	 	 //     intel(student0) <--- registration(course0,student0) 
	 	 //     then return prof0, student0 and course0 )
	 	 //( or configuration 3:
	 	 //		intel(student0) <-- ranking(student0)
	 	 //     then only return  student0 )
	 	 st.execute("drop table if exists Pvars_Family;");
	 	 st.execute("create table Pvars_Family as "
	 	 		+ " select  child,pvid FROM Path_BayesNets,FNodes_pvars_UNION_RNodes_pvars where Rchain='"+rchain+"' and Path_BayesNets.parent = Fid "
	 	 		+ "union select  child,pvid FROM Path_BayesNets,FNodes_pvars_UNION_RNodes_pvars where Rchain='"+rchain+"' and Path_BayesNets.child = Fid;");
	 	//for each configuration(i.e. each node with all its parents ), find some population variables that *NOT* related
	 	//(configuration 1: should return course0
	 	//( or configuration 2: all population variables are joined, so there should be *NO* entry of this node
	 	//( or configuration 3: course0 and prof0
	 	 st.execute("drop table if exists Pvars_Not_In_Family;");
	 	 st.execute("create table Pvars_Not_In_Family as "
		 	 		+ " select Fid as child, pvid from FNodes, PVariables where (Fid,pvid) not in (select * from Pvars_Family) ;");
	 	// Dec 4th, zqian
	 	// adding one column into table PVariables, for storing the number of tuples in the corresponding real data table
		// student0  ---- student --- count(*)
	 	ResultSet rs1 = st.executeQuery("show columns from `PVariables` like 'Tuples' ;");
	 	if (!rs1.next()){
	 	 st.execute("ALTER IGNORE TABLE `PVariables` ADD COLUMN `Tuples` BIGINT(20) NULL AFTER `index_number`;");
	 	}
	 	//updating PVariables 
		ResultSet rs = st.executeQuery("SELECT table_name FROM EntityTables;");
	 	java.sql.Statement st1 = con1.createStatement();
		while(rs.next()){
			String entity_table = rs.getString("table_name");
			String sql="update PVariables set Tuples = (select count(*) from "+ real_database+"."+entity_table+" ) where PVariables.table_name = '"+entity_table+"';";
			System.out.println("\n**********\n adding tuple to PVariables :"+ sql);
			st1.execute(sql);					
		}
	 	 //updating Pvars_Not_In_Family
	 	 st.execute("ALTER TABLE `Pvars_Not_In_Family` ADD COLUMN `Tuples` BIGINT(20) NULL AFTER `pvid`;");
	 	 st.execute("update Pvars_Not_In_Family set Tuples = (select Tuples from PVariables where PVariables.pvid = Pvars_Not_In_Family.pvid  ) ;");

	 	 st.close();
	 	 st1.close();
		 
	 	
} 	 
 	 
 
 
public static void updateParentSum(String table1, String table2, String table3, String node, String order, Connection conn) throws SQLException, IOException {
		File ftemp = new File("sort_merge.csv");
		if (ftemp.exists())
			ftemp.delete();
		File file = new File("sort_merge.csv");
		BufferedWriter output = new BufferedWriter(new FileWriter(file));

		Statement st1 = conn.createStatement();
		Statement st2 = conn.createStatement();

		long time1 = System.currentTimeMillis();
		ResultSet rst1 = st1.executeQuery("select distinct mult, " + node + "," + order + " from " + table1 + " order by " + order + " ;");
		ResultSet rst2 = st2.executeQuery("select distinct mult, " + order + ", ParentSum from " + table2 + " order by " + order + " ;");
		long time2 = System.currentTimeMillis();

		// finding the no. of rows in each table
		int size1 = 0, size2 = 0;
		while (rst1.next())	size1++;
		while (rst2.next())	size2++;

		// finding the no of columns in a table
		ResultSetMetaData rsmd = (ResultSetMetaData) rst1.getMetaData();
		int no_of_colmns = rsmd.getColumnCount();

		int i = 1;
		int j = 1;// index variables for both tables
		rst1.absolute(1);
		rst2.absolute(1);
		long time3 = System.currentTimeMillis();
		// merging starting here
		while (i <= size1 && j <= size2) {
			long val1 = 0, val2 = 0;
			for (int k = 3; k <= no_of_colmns; k++) {
				try {
					val1 = Long.parseLong(rst1.getString(k));
					val2 = Long.parseLong(rst2.getString(k-1));
				} catch (java.lang.NumberFormatException e) {
				} finally {
					if (rst1.getString(k).compareTo(rst2.getString(k - 1)) > 0) {
						val1 = 1;
						val2 = 0;
					} else if (rst1.getString(k).compareTo(rst2.getString(k - 1)) < 0) {
						val1 = 0;
						val2 = 1;
					}
				}

				if (val1 < val2) {
					String quer = rst1.getString(1);
					for (int c = 2; c <= no_of_colmns; c++) {
						quer = quer + "$" + rst1.getString(c);
					}
					quer += "$" + rst2.getString(no_of_colmns);
					output.write((quer) + "\n");
					i++;
					break;
				}

				else if (val1 > val2) {
					j++;
					break;
				}
			}
			if (val1 == val2) {
				String query = "" + Long.parseLong(rst1.getString(1));
				for (int c = 2; c <= no_of_colmns; c++) {
					query = query + "$" + rst1.getString(c);
				}
				query += "$" + rst2.getString(no_of_colmns);
				output.write(query + "\n");
				i++;
				//j++; COMMENTED BY SAJ
			}
			rst1.absolute(i);
			rst2.absolute(j);
		}

		if (i > 1)
			rst1.absolute(i - 1);
		else
			rst1.beforeFirst();
		while (rst1.next()) {
			String query = rst1.getString(1);
			for (int c = 2; c <= no_of_colmns; c++) {
				query = query + "$" + rst1.getString(c);
			}
			if(rst2.next())
				query += "$" + rst2.getString(no_of_colmns);
			output.write((query) + "\n");
		}
		output.close();
		long time4 = System.currentTimeMillis();
		// System.out.print("\t insert time:"+(time4-time3));

		st2.execute("drop table if exists " + table3 + "; ");
		st2.execute("create table " + table3 + " like " + table1 + " ;");
		st2.execute("LOAD DATA LOCAL INFILE 'sort_merge.csv' INTO TABLE " + table3
				+ " FIELDS TERMINATED BY '$' LINES TERMINATED BY '\\n'  ;");
//		st2.execute("Alter table " + table3 + " CHANGE COLUMN ParentSum ParentSum decimal(42,0);");
		st2.execute("Alter table " + table3 + " add `local_mult` decimal(42,0);" );
		
//		st2.execute("drop table if exists temp3;");
//	 	st2.execute("create table temp3 as select * from "+table3+";");
		
	 	rst1.close();
		rst2.close();
		st1.close();
		st2.close();

		long time5 = System.currentTimeMillis();
		// System.out.print("\t export csv file to sql:"+(time5-time4));
		System.out.println("\ntotal time: " + (time5 - time1) + "\n");
		
		if (ftemp.exists())
			ftemp.delete();
}
 
 
 
 
 
 
 public static void setVarsFromConfig(){
 	 Config conf = new Config();
 	 dbUsername = conf.getProperty("dbusername");
 	 dbPassword = conf.getProperty("dbpassword");
 	 dbaddress = conf.getProperty("dbaddress");
 	real_database=conf.getProperty("dbname");
 }
  
 public static void connectDB() throws SQLException {
 	 String CONN_STR1 = "jdbc:" + dbaddress + "/" + databaseName;
 
 	 try {
 	 	 java.lang.Class.forName("com.mysql.jdbc.Driver");
 	 } catch (Exception ex) {
 	 	 System.err.println("Unable to load MySQL JDBC driver");
 	 }
 	 con1 = DriverManager.getConnection(CONN_STR1, dbUsername, dbPassword);
 	 java.sql.Statement st = con1.createStatement();
 	 ResultSet myrchain = st.executeQuery("select name as RChain from lattice_set where lattice_set.length = (SELECT max(length) FROM lattice_set);");
 	 myrchain.absolute(1);
 	 rchain = myrchain.getString(1);
 	 System.out.println("rchain: "+ rchain +"\n");
 	 st.close();
 	 
 	
}
}
