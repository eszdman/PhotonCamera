package com.particlesdevs.photoncamera.debugclient;

//Keys
//CHARACTERISTICS_KEY
//CHARACTERISTICS_KEYS
//CAPTURE_KEYS
//BUILDER_CREATE
//BUILDER_SET
//PREVIEW_KEY
//PREVIEW_KEYS
//PREVIEW_KEYS_PRINT
//DEBUG_SHOT
public interface Command {
    public void command();
    static Command getInstance(String mServerMessage){
        String[] commands = mServerMessage.split(":");
        switch (commands[0]){
            case "CHARACTERISTICS_KEY":{
                return new CharacteristicsKey(commands);
            }
            case "CHARACTERISTICS_KEYS":{
                return new CharacteristicsKeys(commands);
            }
            case "CAPTURE_KEYS":{
                return new CaptureKeys(commands);
            }
            case "BUILDER_CREATE":{
                return new BuilderCreate(commands);
            }
            case "BUILDER_SET":{
                return new BuilderSet(commands);
            }
            case "PREVIEW_KEY":{
                return new PreviewKey(commands);
            }
            case "PREVIEW_KEYS":{
                return new PreviewKeys(commands);
            }
            case "PREVIEW_REQUEST_KEYS":{
                return new PreviewRequestKeys(commands);
            }
            case "PREVIEW_REQUEST_KEYS_PRINT":{
                return new PreviewRequestKeysPrint(commands);
            }
            case "PREVIEW_KEYS_PRINT":{
                return new PreviewKeysPrint(commands);
            }
            case "DEBUG_SHOT":{
                return new DebugShot(commands);
            }
        }
        return null;
    }

}





