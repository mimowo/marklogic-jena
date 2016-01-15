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

def deleteLocalReleaseBranchIfNeeded() {
  try {
    git('rev-parse --verify ' + releaseBranch)
  } catch (all) {
    println "[INFO] Local branch " + releaseBranch + " does not exist, continue."
    return null
  }
  println "[INFO] Local branch " + releaseBranch + " exits, removing."
  git('branch -D ' + releaseBranch)
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

def createReleaseBranch() {
  if (releaseFromBranch != "master") {
    git('checkout ' + releaseFromBranch)
  }
  git('branch ' + releaseBranch)
  git('pull origin ' + releaseFromBranch)
}

def commitAndCheckoutReleaseBranch() {
  git('add .')
  runCommand(["git", "commit", "-m", "version updated to " + developmentVersion])
  git('checkout ' + releaseBranch)
}

def commitReleaseBranch() {
  git('add .')
  runCommand(["git", "commit", "-m", "version updated to " + releaseVersion])
}

def action = this.args[0]


if(action == 'create-release-branch') {
  deleteLocalReleaseBranchIfNeeded()
  createReleaseBranch();
}

if(action == 'verify-and-create-release-branch') {
  verifyTagDoesntExist()

  deleteLocalReleaseBranchIfNeeded()
  createReleaseBranch();
}

if(action == 'commit-current-and-checkout-release-branch') {
  commitAndCheckoutReleaseBranch();
}

if(action == 'commit-release-branch') {
  commitReleaseBranch();
}

if(action == 'before-maven-build') {
  verifyTagDoesntExist()

  createReleaseBranch();
  mvn('versions:set -DnewVersion=' + developmentVersion + '-DgenerateBackupPoms=false')
  commitAndCheckoutReleaseBranch()
  mvn('versions:set -DnewVersion=' + releaseVersion + '-DgenerateBackupPoms=false')
  commitReleaseBranch()
}

if (action =='before-gradle-build') {
  verifyTagDoesntExist()

  createReleaseBranch();
  gradle('setVersion -PnewVersion=' + developmentVersion)
  commitAndCheckoutReleaseBranch()
  gradle('setVersion -PnewVersion=' + releaseVersion)
  commitReleaseBranch()
}

if (action == 'after-build-success') {
  git("tag " + releaseTag + " " +releaseBranch)
  git('push origin ' + releaseFromBranch + ':' + releaseFromBranch)
  git('push origin ' + releaseBranch + ':' + releaseBranch + ' --tags')
}
