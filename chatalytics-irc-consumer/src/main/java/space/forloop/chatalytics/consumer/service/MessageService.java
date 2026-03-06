package space.forloop.chatalytics.consumer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.IrcPayload;
import space.forloop.chatalytics.data.generated.tables.pojos.Message;
import space.forloop.chatalytics.data.repositories.MessageRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    @Transactional
    public void processMessages(List<IrcPayload> payloads) {
        log.info("Writing batch of {} messages", payloads.size());

        messageRepository.batchWrite(convertToMessages(payloads));
    }

    private List<Message> convertToMessages(List<IrcPayload> payloads) {
        List<Message> list = new ArrayList<>(payloads.size());

        for (IrcPayload payload : payloads) {
            if (payload.getNick() == null) {
                log.info("Missing nick for message: {}", payload);
                continue;
            }

            Message message = new Message();
            message.setTwitchId(payload.getSession().twitchId());
            message.setSessionId(payload.getSession().id());
            message.setMessageText(payload.getMessage());
            message.setAuthor(payload.getNick());
            message.setTimestamp(payload.getTimestamp());

            list.add(message);
        }

        return list;
    }
}
