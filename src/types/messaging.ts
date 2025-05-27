import { EventEmitter } from 'events';

export enum MessageType {
    INTENT = 'intent',
    MQTT = 'mqtt',
    REST = 'rest'
}

export interface Message {
    id: string;
    source: string;
    type: MessageType;
    command: string;
    payload: any;
    timestamp: Date;
    metadata?: Record<string, any>;
}

export interface IMessageSource extends EventEmitter {
    name: string;
    type: MessageType;
    start(): Promise<void>;
    stop(): Promise<void>;
    isConnected(): boolean;
}

export interface ICommandHandler {
    command: string;
    description: string;
    handle(message: Message): Promise<any>;
}

export interface CommandResult {
    success: boolean;
    data?: any;
    error?: string;
}
