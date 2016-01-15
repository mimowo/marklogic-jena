def env = System.getenv()

releaseVersion = env['RELEASE_VERSION']
releaseBranch = "release-" + releaseVersion
releaseTag = releaseVersion
developmentVersion = env['DEVELOPMENT_VERSION']
releaseFromBranch = env['RELEASE_FROM_BRANCH']

(git_cmd, mvn_cmd, gradle_cmd) = ["git", "mvn", "gradle"]

// a wrapper closure around executing a string                                  
// can take either a string or a list of strings (for arguments with spaces)    
// prints all output, complains and halts on error                              
def runCommand(strList) {
  print "[INFO] ( "
  if(strList instanceof List) {
    strList.each { print "${it} " }
  } else {
    print strList
  }
  println " )"

  def proc = strList.execute()
  proc.in.eachLine { line -> println line }
  proc.out.close()
  proc.waitFor()

  if (proc.exitValue()) {
    throw new RuntimeException("Failed to execute command with error: ${proc.getErrorStream()}")
  }
  return proc.exitValue()
}

def git(args) {
  runCommand("git" + " " + args)
}

def mvn(args) {
  runCommand("sh mvn" + " " + args)
}

def gradle(args) {
  runCommand("sh gradle" + " " + args)
}

def verifyTagDoesntExist() {
  try {
    git('ls-remote --tags --exit-code origin ' + releaseTag)
    //git('ls-remote --heads --exit-code origin ' + releaseBranch)
  } catch (RuntimeException e) {
    println "[INFO] Tag " + releaseTag + " does not exist yet, continue."
    return null
  }
  throw new RuntimeException("Tag " + releaseTag + " already exists!")
}

def beforeBuildSetUpSourceBranch() {
  git('checkout ' + releaseFromBranch)
  git('pull origin ' + releaseFromBranch)
  git('branch ' + releaseBranch)  
}

def beforeBuildSetUpTargetBranch() {
  git('add .')
  runCommand(["git", "commit", "-m", "version updated to " + developmentVersion])
  git('checkout ' + releaseBranch)
}

def beforeBuildSetUpTargetBranchCommit() {
  git('add .') 
  runCommand(["git", "commit", "-m", "version updated to " + releaseVersion])
}

def action = this.args[0]

if(action == 'before-maven-build') {
  verifyTagDoesntExist()

  beforeBuildSetUpSourceBranch();
  mvn('versions:set -DnewVersion=' + developmentVersion + '-DgenerateBackupPoms=false')
  beforeBuildSetUpTargetBranch()
  mvn('versions:set -DnewVersion=' + releaseVersion + '-DgenerateBackupPoms=false')
  beforeBuildSetUpTargetBranchCommit()
} else if (action =='before-gradle-build') {
  verifyTagDoesntExist()

  beforeBuildSetUpSourceBranch();
  gradle('setVersion -PnewVersion=' + developmentVersion)
  beforeBuildSetUpTargetBranch()
  gradle('setVersion -PnewVersion=' + releaseVersion)
  beforeBuildSetUpTargetBranchCommit()
} else if (action == 'after-build-success') {
  git("tag " + releaseTag + " " +releaseBranch)
  git('push origin ' + releaseFromBranch + ':' + releaseFromBranch)
  git('push origin ' + releaseBranch + ':' + releaseBranch + ' --tags')
}
