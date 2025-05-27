import { EventEmitter } from 'events';
import * as mqtt from 'mqtt';
import { v4 as uuidv4 } from 'uuid';
import { IMessageSource, Message, MessageType } from '../../types/messaging';

export class MqttMessageSource extends EventEmitter implements IMessageSource {
    public readonly name = 'mqtt';
    public readonly type = MessageType.MQTT;
    
    private client: mqtt.MqttClient | null = null;
    private topics: string[] = [];

    constructor(
        private brokerUrl: string,
        private options: mqtt.IClientOptions = {},
        topics: string[] = ['notifications/+', 'commands/+']
    ) {
        super();
        this.topics = topics;
    }

    async start(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.client = mqtt.connect(this.brokerUrl, this.options);

            this.client.on('connect', () => {
                console.log('MQTT client connected');
                
                // Subscribe to topics
                this.topics.forEach(topic => {
                    this.client!.subscribe(topic, (err) => {
                        if (err) {
                            console.error(`Failed to subscribe to topic ${topic}:`, err);
                        } else {
                            console.log(`Subscribed to MQTT topic: ${topic}`);
                        }
                    });
                });
                
                resolve();
            });

            this.client.on('message', (topic, payload) => {
                this.handleMqttMessage(topic, payload);
            });

            this.client.on('error', (error) => {
                console.error('MQTT client error:', error);
                reject(error);
            });

            this.client.on('close', () => {
                console.log('MQTT client disconnected');
            });
        });
    }

    async stop(): Promise<void> {
        if (this.client) {
            await new Promise<void>((resolve) => {
                this.client!.end(() => {
                    this.client = null;
                    resolve();
                });
            });
        }
    }

    isConnected(): boolean {
        return this.client?.connected || false;
    }

    private handleMqttMessage(topic: string, payload: Buffer): void {
        try {
            const data = JSON.parse(payload.toString());
            const command = this.extractCommandFromTopic(topic) || data.command;
            
            if (!command) {
                console.warn(`No command found in MQTT message from topic: ${topic}`);
                return;
            }

            const message: Message = {
                id: uuidv4(),
                source: this.name,
                type: this.type,
                command,
                payload: data,
                timestamp: new Date(),
                metadata: {
                    topic,
                    qos: 0
                }
            };

            this.emit('message', message);
        } catch (error) {
            console.error('Failed to parse MQTT message:', error);
        }
    }

    private extractCommandFromTopic(topic: string): string | null {
        // Extract command from topics like 'commands/notify' or 'notifications/send'
        const parts = topic.split('/');
        return parts.length > 1 ? parts[1] : null;
    }
}
