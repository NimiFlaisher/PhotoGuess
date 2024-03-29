package com.example.photoguess.view;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.photoguess.R;
import com.example.photoguess.databinding.FragmentActivePlayersBinding;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ActivePlayersFragment extends BaseFragment {

    FragmentActivePlayersBinding binding;
    String roomPin;
    String name;
    ValueEventListener gameProgressListener;
    String photoCaptionText;
    String winner;
    char[] photoCaptionArray;
    char[] guessingArray;
    // List of char
    List<String> usedLetters = new ArrayList<>();
    String guessingArrayString;
    DatabaseReference roomRef;
    DatabaseReference gameProgressRef;
    StorageReference storageRef;

    int blurLevel = 100;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        roomPin = gameController.getRoomPin();
        name = gameController.getName();
        binding = FragmentActivePlayersBinding.inflate(inflater, container, false);
        view = binding.getRoot();
        binding.displayedImage.setImageResource(R.drawable.questionmark);
        if (gameController.isMusicOn())
            binding.musicToggleButton.setImageResource(R.drawable.volume);
        else
            binding.musicToggleButton.setImageResource(R.drawable.mute);
        binding.musicToggleButton.setOnClickListener(view -> {
            if(gameController.isMusicOn()){
                gameController.stopBackgroundMusic();
                binding.musicToggleButton.setImageResource(R.drawable.mute);
            }else{
                gameController.startBackgroundMusic();
                binding.musicToggleButton.setImageResource(R.drawable.volume);
            }
        });
        roomRef = gameModel.getRoomRef();
        gameProgressRef = roomRef.child("GameProgress");
        roomRef.child("Caption").addListenerForSingleValueEvent(new ValueEventListener(){
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                photoCaptionText = snapshot.getValue(String.class);
                assert photoCaptionText != null;
                photoCaptionArray = photoCaptionText.toCharArray();
                guessingArray = new char[photoCaptionArray.length];
                underscoreCreator();
                guessingArrayString = new String(guessingArray);
                binding.hangmanText.setText(guessingArrayString);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        storageRef = gameController.getStorageRef();

        storageRef.getBytes(1024 * 1024).addOnSuccessListener(bytes -> {
            SystemClock.sleep(1000);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            binding.displayedImage.setImageBitmap(bitmap);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.displayedImage.setRenderEffect(RenderEffect.createBlurEffect(100, 100, Shader.TileMode.MIRROR));
            } else {
                binding.displayedImage.setAlpha(0.1f);
            }
            playSound(R.raw.gamestart);
        }).addOnFailureListener(exception -> Toast.makeText(getContext(), "Download failed", Toast.LENGTH_SHORT).show());
        binding.letterGuessButton.setOnClickListener(v -> {
            String letter = binding.guessText.getText().toString();
            if (letter.length() == 1) {
                letterChecker(letter.charAt(0));
            } else {
                Toast.makeText(getContext(), "Please enter a single letter", Toast.LENGTH_SHORT).show();
            }
        });
        binding.fullGuessButton.setOnClickListener(v -> {
            String guess = binding.guessText.getText().toString();
            fullGuessChecker(guess);
        });
        binding.skipTurnButton.setOnClickListener(v -> {
            skipTurn();
        });

        gameProgressListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String message = snapshot.child("MessageBoard").getValue(String.class);
                if (message == null)
                    message = "Loading...";
                binding.MessageBoard.setText(message);
                if (Objects.equals(name, snapshot.child("CurrentPlayerTurn").getValue(String.class))){
                    binding.guessText.setEnabled(true);
                    binding.letterGuessButton.setEnabled(true);
                    binding.fullGuessButton.setEnabled(true);
                    binding.skipTurnButton.setEnabled(true);
                } else {
                    binding.guessText.setEnabled(false);
                    binding.letterGuessButton.setEnabled(false);
                    binding.fullGuessButton.setEnabled(false);
                    binding.skipTurnButton.setEnabled(false);
                }
                if (snapshot.child("BlurLevel").getValue(Integer.class) != null){
                    if (blurLevel != snapshot.child("BlurLevel").getValue(Integer.class)) {
                        blurLevel = snapshot.child("BlurLevel").getValue(Integer.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (blurLevel <= 0) {
                                binding.displayedImage.setRenderEffect(null);
                            } else {
                                binding.displayedImage.setRenderEffect(RenderEffect.createBlurEffect(blurLevel, blurLevel, Shader.TileMode.MIRROR));
                            }
                        } else {
                            binding.displayedImage.setAlpha(0.1f);
                        }
                    }
                }
                else {
                    blurLevel = 100;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        binding.displayedImage.setRenderEffect(RenderEffect.createBlurEffect(blurLevel, blurLevel, Shader.TileMode.MIRROR));
                    } else {
                        binding.displayedImage.setAlpha(0.1f);
                    }
                }

                if (snapshot.child("UsedLetters").getValue() != null) {
                    usedLetters = (List<String>) snapshot.child("UsedLetters").getValue();
                    binding.updatingUsedLettersText.setText(usedLetters.toString());
                }
                if (snapshot.child("CurrentGuess").getValue() != null){
                    String cg = snapshot.child("CurrentGuess").getValue(String.class);
                    if (!Objects.equals(cg, guessingArrayString)){
                        guessingArrayString = snapshot.child("CurrentGuess").getValue(String.class);
                        guessingArray = guessingArrayString.toCharArray();
                        binding.hangmanText.setText(guessingArrayString);
                    }
                }

                if ((snapshot.child("Winner").getValue() != null) && (snapshot.child("Restart").getValue() != null)){
                    winner = snapshot.child("Winner").getValue(String.class);
                    nextFragment();
                }

                if (snapshot.child("GameOver").getValue() != null){
                    replaceFragment(new MenuFragment());
                }

            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };

        gameProgressRef.addValueEventListener(gameProgressListener);
        return view;
    }

    public void underscoreCreator(){
        for (int i = 0; i < photoCaptionArray.length; i++) {
            if (Character.isLetter(photoCaptionArray[i])) {
                guessingArray[i] = '_';
            } else {
                guessingArray[i] = ' ';
            }
        }
    }

    public void letterChecker(char letter){
        if (!Character.isLetter(letter)) {
            Toast.makeText(getContext(), "Please enter a letter", Toast.LENGTH_SHORT).show();
            return;
        }
        letter = Character.toUpperCase(letter);
        if (usedLetters != null) {
            for (String usedLetter : usedLetters) {
                if (usedLetter.charAt(0) == letter) {
                    Toast.makeText(getContext(), "Letter already used", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        boolean letterFound = false;
        for (int i = 0; i < photoCaptionArray.length; i++) {
            if (photoCaptionArray[i] == letter) {
                letterFound = true;
                guessingArray[i] = letter;
            }
        }
        usedLetters.add(String.valueOf(letter));
        gameProgressRef.child("UsedLetters").setValue(usedLetters);
        if (letterFound) {
            guessingArrayString = new String(guessingArray);
            binding.hangmanText.setText(guessingArrayString);
            gameProgressRef.child("CurrentGuess").setValue(guessingArrayString);
            playSound(R.raw.correctchoice);
        } else {
            playSound(R.raw.badchoice);
            skipTurn();
        }
    }

    public void fullGuessChecker(String guess) {
        guess = guess.toUpperCase().trim();
        if (isAlphabeticalEnglish(guess)){
            if (guess.equals(photoCaptionText)) {
                guessingArray = photoCaptionArray;
                guessingArrayString = new String(guessingArray);
                binding.hangmanText.setText(guessingArrayString);
                gameProgressRef.child("Winner").setValue(name);
                playSound(R.raw.gamewinner);
            }
            else {
                skipTurn();
            }
        }
    }

    public boolean isAlphabeticalEnglish(String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a caption", Toast.LENGTH_SHORT).show();
            binding.guessText.setError("Please enter a caption");
            binding.guessText.requestFocus();
            return false;
        }
        if (text.trim().length() > 30){
            binding.guessText.setError("Caption is too long");
            binding.guessText.requestFocus();
            return false;
        }
        for (char c : text.toCharArray()) {
            if (!Character.isLetter(c) && !Character.isWhitespace(c)) {
                binding.guessText.setError("Please enter an alphabetical-only caption");
                binding.guessText.requestFocus();
                return false;
            }
        }
        return true;
    }

    public void skipTurn(){
        gameProgressRef.child("SkipTurn").setValue(true);
        binding.guessText.setEnabled(false);
        binding.letterGuessButton.setEnabled(false);
        binding.fullGuessButton.setEnabled(false);
        binding.skipTurnButton.setEnabled(false);
    }

    private void nextFragment() {
        gameController.setPhotoUploader(winner);
        if (Objects.equals(winner, name)) {
            replaceFragment(new PhotoPickerFragment());
        }else{
            replaceFragment(new WaitingRoomFragment());
        }
    }

    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        gameProgressRef.removeEventListener(gameProgressListener);
    }

}