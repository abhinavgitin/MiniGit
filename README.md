# MiniGit: A Lightweight Version Control System in Java

MiniGit is a lightweight Git-like version control system built in Java that replicates core Git functionalities such as staging, committing, branching, and tracking file changes using a custom object storage mechanism.

---

## ⚡ Run in 10 Seconds =D

```bash
# Clone the repo locally and then go to that dir
git clone https://github.com/abhinavgitin/MiniGit.git
cd MiniGit

# Compile ( make sure you have java installed )
javac -d out src/minigit/**/*.java

# Initialize repository ( and here it starts =) )
java -cp out minigit.Main init
```

---

## Demo

```bash
# Add file like we do git add <name of the file>
echo "Hello MiniGit" > a.txt
java -cp out minigit.Main add a.txt

# Commit : just like git commit -m "<for this commit you dont have to stare the scrren like you usually do haha>"
java -cp out minigit.Main commit -m "first commit"

# View log ( my fav : like git log --oneline )
java -cp out minigit.Main log

# Create branch ( creat one and then checkout to same )
java -cp out minigit.Main branch feature
java -cp out minigit.Main checkout feature
```

---

## Features i thought would be cool to have

* Custom version control system inspired by Git
* Staging area (index) implementation
* Commit history tracking
* Branch creation and switching
* File status tracking (staged, modified, untracked)
* Diff between working directory and last commit
* Fast-forward merge support
* Custom ignore rules (.minigitignore)

---

## Under the Hood

MiniGit ~like Git’s internal architecture:

* **Blob** → stores file contents
* **Commit** → snapshot of project state
* **Index** → staging area
* **ObjectStore** → manages SHA-256 hashed objects ( better :P )
* **HEAD** → tracks current branch ( like current dir )

---
<br>

## 🧪 Testing

```bash
java -cp out minigit.tests.MiniGitRegressionTest
```

---

## 📂 Project Structure

```
src/
  minigit/
    Main.java
    commands/
    core/
    utils/
    tests/
```

---

## 📌 Design Decisions

* Used SHA-256 hashing for content-addressable storage
* Implemented staging area to separate working directory and commits
* Designed branch system using reference pointers similar to Git
* Focused on simplicity while preserving core Git concepts

---
<br>

This project is licensed under the MIT License.
