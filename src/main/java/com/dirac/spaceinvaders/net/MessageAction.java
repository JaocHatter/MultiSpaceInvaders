package com.dirac.spaceinvaders.net;

import java.io.Serializable;


public enum MessageAction implements Serializable {
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_UP,
    MOVE_DOWN,
    SHOOT,
    CONNECT,
    DISCONNECT
}