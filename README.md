# FactorBase
[![Build Status](https://travis-ci.org/sfu-cl-lab/FactorBase.svg?branch=master)](https://travis-ci.org/sfu-cl-lab/FactorBase)   
This is for Sajjad to try his changes based on his thesis research.

changes by Sajjad:

+ In CP.java, added merge sort to compute parentsum. The function name is UpdateParentSum.
+ Added Scores.java. Package for implementing different scores, normalized gain etc. Also uses global score cache. global score cache is used only for these new scores, not for the Tetrad classes.
+ Added global score cache (between different lattice points). Used in edu.cmu.tetrad.search, and the main package (several classes that use global cache).
+ For complete graph, change BayesBaseCDAG (from Zhensong), now uses the new CP.java with the new sort merge.

The source code repository for the Factor Base system.  Most of the code are classes for CMU's Tetrad system. We may also add datasets if we get around to it.  
For more information about this project, visit our [project website](http://www.cs.sfu.ca/~oschulte/BayesBase/BayesBase.html)  
##How to Use  
First you should import data into your database. We provide two sets of example datasets in `testsql` folder. Then you can either run `.jar` or compile the source yourself. If you want to visualize the BayesNet learned, you can run [BIF_Generator](https://github.com/sfu-cl-lab/BIF_Generator)  
###Run .jar  
+ Modify `jar/cfg/subsetctcomputation.cfg` with your own configuration according to format explained [here](http://www.cs.sfu.ca/~oschulte/BayesBase/options.html)  
+ In `jar` folder, run `java -jar FactorBase.jar`  
+ For big databases, you need to specify larger java heap size. For example: `java -jar -Xmx8G FactorBase.jar`   
  
###Compile & Run  
+ Go into `src/cfg` folder and modify `subsetctcomputation.cfg`  
+ `javac -cp ".:./lib/*" Config.java BZScriptRunner.java MakeSetup.java`  
+ `javac -cp ".:./lib/*" RunBB.java`  
+ `mkdir src`  
+ `mv scripts src/`  
+ `java -cp ".:./lib/*" MakeSetup`  
+ `java -cp ".:./lib/*" RunBB`  
+ Optionally set up the target database and run FunctorWrapper  
  + `javac -cp ".:./lib/*" MakeTargetSetup.java`  
  + `javac -cp ".:./lib/*" FunctorWrapper.java`  
  + `java -cp ".:./lib/*" MakeTargetSetup`  
  + `java -cp ".:./lib/*" FunctorWrapper` 
  
## Project Specification  
Please visit our [project website](http://www.cs.sfu.ca/~oschulte/BayesBase/BayesBase.html)
