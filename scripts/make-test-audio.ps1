Add-Type -AssemblyName System.Speech
$speech = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voice = $speech.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'en-*' } | Select-Object -First 1
if (-not $voice) { throw 'No English synthesis voice installed.' }
$speech.SelectVoice($voice.VoiceInfo.Name)
$speech.SetOutputToWaveFile((Join-Path (Split-Path -Parent $PSScriptRoot) '.local\english-test.wav'))
$speech.Speak('Hello, I would like to practice my English. Yesterday I went to a coffee shop with my friend.')
$speech.Dispose()
Write-Output 'ENGLISH_AUDIO_FIXTURE_READY'
