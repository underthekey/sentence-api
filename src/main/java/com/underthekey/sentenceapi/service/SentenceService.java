package com.underthekey.sentenceapi.service;

import static com.underthekey.sentenceapi.exception.ErrorCode.*;
import static com.underthekey.sentenceapi.util.RandomIdsGenerator.*;
import static com.underthekey.sentenceapi.util.ValidationUtil.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.underthekey.sentenceapi.domain.Sentence;
import com.underthekey.sentenceapi.domain.type.Threshold;
import com.underthekey.sentenceapi.dto.SentenceDto;
import com.underthekey.sentenceapi.exception.BaseException;
import com.underthekey.sentenceapi.repository.CategoryRepository;
import com.underthekey.sentenceapi.repository.SentenceRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SentenceService {
	private final CategoryRepository categoryRepository;
	private final SentenceRepository sentenceRepository;
	private final RedisTemplate<Long, SentenceDto> redisTemplate;

	public SentenceDto getSentenceById(Long id) {
		SentenceDto cachedSentence = getCachedSentence(id);
		if (cachedSentence != null) {
			return cachedSentence;
		}

		Sentence sentence = sentenceRepository.findById(id).orElseThrow(() -> new BaseException(SENTENCE_NOT_FOUND));
		SentenceDto sentenceDto = SentenceDto.of(sentence);
		cacheSentence(id, sentenceDto);
		return sentenceDto;
	}

	public List<SentenceDto> getRandomSentences(Long count) {
		validateCount(count);

		List<Long> randomIds = generateRandomIds(count.intValue());
		return getRandomSentencesByIds(randomIds);
	}

	public List<SentenceDto> getRandomSentencesByLanguage(String language, Long count) {
		validateLanguage(language);
		validateCount(count);

		List<Long> randomIds = categoryRepository.findRandomSentenceIdsByLanguage(language, count);
		return getRandomSentencesByIds(randomIds);
	}

	public List<SentenceDto> getRandomSentencesByCategorySort(String sort, Long count) {
		validateSort(sort);
		validateCount(count);

		List<Long> randomIds = categoryRepository.findRandomSentenceIdsBySort(sort, count);
		return getRandomSentencesByIds(randomIds);
	}

	private List<SentenceDto> getRandomSentencesByIds(List<Long> randomIds) {
		if (randomIds.isEmpty()) {
			return new ArrayList<>();
		}

		List<SentenceDto> cachedObjects = getCachedObjects(randomIds);

		List<SentenceDto> result = processCachedSentences(cachedObjects);
		List<Long> missingIds = getMissingIds(randomIds, cachedObjects);

		if (!missingIds.isEmpty()) {
			result.addAll(getSentencesFromDbAndCache(missingIds));
		}
		Collections.shuffle(result); // randomize order
		return result;
	}

	private List<SentenceDto> getCachedObjects(List<Long> randomIds) {
		List<SentenceDto> cachedObjects = redisTemplate.opsForValue().multiGet(randomIds);
		return cachedObjects == null ? new ArrayList<>() : cachedObjects;
	}

	private List<SentenceDto> processCachedSentences(List<SentenceDto> cachedObjects) {
		return cachedObjects.stream().filter(Objects::nonNull).collect(Collectors.toList());
	}

	private List<Long> getMissingIds(List<Long> randomIds, List<SentenceDto> cachedObjects) {
		return IntStream.range(0, cachedObjects.size())
			.filter(i -> cachedObjects.get(i) == null)
			.mapToObj(randomIds::get)
			.collect(Collectors.toList());
	}

	private List<SentenceDto> getSentencesFromDbAndCache(List<Long> missingIds) {
		List<Sentence> sentencesFromDb = sentenceRepository.findAllById(missingIds);

		return sentencesFromDb.stream().map(sentence -> {
			SentenceDto dto = SentenceDto.of(sentence);
			cacheSentence(sentence.getId(), dto);
			return dto;
		}).collect(Collectors.toList());
	}

	private void cacheSentence(Long id, SentenceDto sentenceDto) {
		redisTemplate.opsForValue().set(id, sentenceDto, Duration.ofMinutes(Threshold.CACHE_DURATION_MINUTE.getNum()));
	}

	private SentenceDto getCachedSentence(Long id) {
		return redisTemplate.opsForValue().get(id);
	}
}
