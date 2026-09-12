# Выполнить из корня проекта: source tools/android-env.sh
if [ ! -f settings.gradle.kts ]; then
    echo "Сначала перейдите в корневую папку kotlin-course-project."
    return 1
fi
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$PWD/.android-sdk"
export ANDROID_USER_HOME="$PWD/.android-user-home"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
