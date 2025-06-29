/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package smoketest.data.jpa.service;

import smoketest.data.jpa.domain.City;
import smoketest.data.jpa.domain.HotelSummary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component("cityService")
@Transactional
class CityServiceImpl implements CityService {

	private final CityRepository cityRepository;

	private final HotelRepository hotelRepository;

	CityServiceImpl(CityRepository cityRepository, HotelRepository hotelRepository) {
		this.cityRepository = cityRepository;
		this.hotelRepository = hotelRepository;
	}

	@Override
	public Page<City> findCities(CitySearchCriteria criteria, Pageable pageable) {
		Assert.notNull(criteria, "'criteria' must not be null");
		String name = criteria.getName();
		if (!StringUtils.hasLength(name)) {
			return this.cityRepository.findAll(null);
		}
		String country = "";
		int splitPos = name.lastIndexOf(',');
		if (splitPos >= 0) {
			country = name.substring(splitPos + 1);
			name = name.substring(0, splitPos);
		}
		return this.cityRepository.findByNameContainingAndCountryContainingAllIgnoringCase(name.trim(), country.trim(),
				pageable);
	}

	@Override
	public City getCity(String name, String country) {
		Assert.notNull(name, "'name' must not be null");
		Assert.notNull(country, "'country' must not be null");
		return this.cityRepository.findByNameAndCountryAllIgnoringCase(name, country);
	}

	@Override
	public Page<HotelSummary> getHotels(City city, Pageable pageable) {
		Assert.notNull(city, "'city' must not be null");
		return this.hotelRepository.findByCity(city, pageable);
	}

}
