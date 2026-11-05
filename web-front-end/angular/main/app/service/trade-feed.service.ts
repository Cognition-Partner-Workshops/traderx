import { Injectable } from '@angular/core';
import { environment } from 'main/environments/environment';
import { io, Socket } from "socket.io-client";
import { AuthService } from './auth.service';

/**
 * Authenticated trade-feed client. The socket only connects while a session exists and
 * presents the caller's bearer token in the handshake; the feed enforces which account
 * topics the caller may subscribe to.
 */
@Injectable({
    providedIn: 'root'
})
export class TradeFeedService {
    private socket?: Socket;

    constructor(private auth: AuthService) {
        this.auth.session$.subscribe((session) => {
            if (session) {
                this.connect();
            } else {
                this.disconnect();
            }
        });
    }

    private connect() {
        if (this.socket) {
            return;
        }
        this.socket = io(environment.tradeFeedUrl, {
            // evaluated on every (re)connect so a refreshed token is always presented
            auth: (cb) => cb({ token: this.auth.token })
        });

        this.socket.on("connect", this.onConnect);
        this.socket.on("disconnect", this.onDisconnect);
        this.socket.on("connect_error", (err) => console.warn('Trade feed rejected connection: ' + err.message));
        this.socket.on("error", (err) => console.warn('Trade feed error', err));
    }

    private disconnect() {
        this.socket?.disconnect();
        this.socket = undefined;
    }

    private onConnect = () => {
        console.log('Trade feed is connected, connection id' + this.socket?.id);
    }

    private onDisconnect = () => {
        console.log('Trade feed is disconnected, connection id was ' + this.socket?.id);
    }

    public subscribe(topic: string, callback: (...args: any[]) => void) {
        const callbackFn = (args: any) => {
            console.log("received message -> "+ JSON.stringify(args));
            if (args.from !== 'System' && args.topic === topic) {
                callback(args.payload);
            }
        }
        this.socket?.on('publish', callbackFn);
        this.socket?.emit('subscribe', topic);
        console.log('subscribing', topic);
        return () => {
            this.unSubscribe(topic, callbackFn);
        }
    }

    public unSubscribe(topic: string, callback: (...args: any[]) => void) {
        console.log('unsubscribing' + topic)
        this.socket?.emit('unsubscribe', topic);
        this.socket?.off('publish', callback)
    }
}
