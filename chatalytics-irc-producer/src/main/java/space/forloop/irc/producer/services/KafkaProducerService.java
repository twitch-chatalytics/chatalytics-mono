package space.forloop.irc.producer.services;


import space.forloop.irc.producer.domain.IrcPayload;

public interface KafkaProducerService {

    void sendMessage(IrcPayload ircPayload);

}
