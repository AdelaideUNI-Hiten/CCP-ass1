const recordButton = document.getElementById('recordButton');
const statusMessage = document.getElementById('statusMessage');
const resultBox = document.getElementById('resultBox');
const transcriptText = document.getElementById('transcriptText');

let mediaRecorder = null;
let audioChunks = [];
let isRecording = false;

recordButton.addEventListener('click', () => {
    if (isRecording) {
        stopRecording();
    } else {
        startRecording();
    }
});

async function startRecording() {
    resultBox.hidden = true;
    transcriptText.textContent = '';

    let stream;
    try {
        stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error) {
        setStatus('Microphone access was denied or is unavailable. Please allow microphone access and try again.');
        return;
    }

    audioChunks = [];
    mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });

    mediaRecorder.addEventListener('dataavailable', (event) => {
        if (event.data.size > 0) {
            audioChunks.push(event.data);
        }
    });

    mediaRecorder.addEventListener('stop', () => {
        // Recording device is no longer needed once we've stopped.
        stream.getTracks().forEach((track) => track.stop());
        handleRecordingStopped();
    });

    mediaRecorder.start();
    isRecording = true;

    recordButton.textContent = 'Stop Recording';
    recordButton.classList.add('recording');
    setStatus('Recording... speak now.');
}

function stopRecording() {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
    }
    isRecording = false;
    recordButton.classList.remove('recording');
}

async function handleRecordingStopped() {
    recordButton.disabled = true;
    recordButton.textContent = 'Processing...';
    setStatus('Transcribing your recording...');

    const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
    const formData = new FormData();
    formData.append('audio', audioBlob, 'recording.webm');

    try {
        const response = await fetch('/api/v1/transcribe', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorBody = await response.json().catch(() => null);
            const message = errorBody?.message || 'The server could not transcribe this recording.';
            throw new Error(message);
        }

        const data = await response.json();
        showResult(data.text);

    } catch (error) {
        setStatus(`Something went wrong: ${error.message}`);
    } finally {
        resetToIdle();
    }
}

function showResult(text) {
    transcriptText.textContent = text;
    resultBox.hidden = false;
    setStatus('Transcription complete. Ready for a new recording.');
}

function resetToIdle() {
    recordButton.disabled = false;
    recordButton.textContent = 'Start Recording';
}

function setStatus(message) {
    statusMessage.textContent = message;
}
