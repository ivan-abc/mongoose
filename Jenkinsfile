pipeline {
    agent any
    stages {
        stage('build') {
            steps {
                sh './gradlew build'
            }
        }
        stage('test') {
            steps {
                sh './gradlew :tests:unit:test'
            }
        }
        stage('archive') {
            steps {
                sh 'gradle dist'
                // Tarballs are now in build/dist
                archiveArtifacts artifacts: 'build/dist/**/*.tgz', fingerprint: true
            }
        }
    }
}
