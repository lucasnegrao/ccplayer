import { EventEmitter } from 'events';
import { v4 as uuidv4 } from 'uuid';
import { IMessageSource, Message, MessageType } from '../../types/messaging';

export class IntentMessageSource extends EventEmitter implements IMessageSource {
    public readonly name = 'intent';
    public readonly type = MessageType.INTENT;
    
    private isActive = false;

    async start(): Promise<void> {
        this.isActive = true;
        console.log('Intent message source started');
    }

    async stop(): Promise<void> {
        this.isActive = false;
        console.log('Intent message source stopped');
    }

    isConnected(): boolean {
        return this.isActive;
    }

    // Method to be called by the intent service when an intent is detected
    handleIntent(intent: string, confidence: number, entities: any[]): void {
        if (!this.isActive) return;

        const message: Message = {
            id: uuidv4(),
            source: this.name,
            type: this.type,
            command: intent,
            payload: {
                confidence,
                entities
            },
            timestamp: new Date(),
            metadata: {
                intentConfidence: confidence,
                entityCount: entities.length
            }
        };

        this.emit('message', message);
    }
}
