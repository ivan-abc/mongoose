pipeline {
    agent any
    stages {
        stage('build') {
            steps {
                sh './gradlew build'
            }
            steps {
                sh 'gradle test'
            }
        }
    }
}
